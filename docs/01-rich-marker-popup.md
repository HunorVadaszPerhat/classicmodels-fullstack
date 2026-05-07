# Feature 01 — Rich popup on the map marker

## What we built

The blue pin on each employee's office map used to show a tiny popup
that said only "Sydney office" (or whichever city). We upgraded the
popup to render a small HTML card with the full street address, the
phone number, and a link through to a per-office detail page. Click
the marker and you now get something like:

```
Sydney office
107 Macquarie St
NSW, Australia
📞 +61 2 9264 2451
View office details →
```

Files touched:

- `classicmodels-ui/src/app/shared/mini-map.component.ts`
- `classicmodels-ui/src/app/employees/employee-detail.component.ts`
- `classicmodels-ui/src/app/employees/employee-detail.component.html`

## Why this is worth learning

Three ideas converge in one small change. **Template literals** make
HTML strings pleasant to construct. **HTML escaping** is a discipline
you'll apply on every project that renders user data. **Angular
`@Input`** is how parent components feed values to children — the
single most common pattern in component-based UI frameworks.

It's also a good "first feature" pattern: small change, immediate
visual feedback, no new dependencies.

## Background

If any of these concepts is new, slow down and read the linked
material before moving on. They recur in almost every feature that
follows.

### Template literals

JavaScript strings wrapped in backticks. Inside them, `${expression}`
is interpolated with the value of the expression, and newlines are
preserved without needing `\n` or `+`.

```ts
const greeting = `Hello, ${user.name}!`;        // single-line, interpolated
const html = `
  <h2>${title}</h2>
  <p>${body}</p>
`;                                               // multi-line, interpolated
```

Reference: [MDN — Template literals (Template strings)](https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Template_literals)

### HTML escaping & cross-site scripting (XSS)

If user-supplied data ends up in an HTML string and any of `< > & " '`
is left unescaped, the browser interprets those characters as markup.
A name like `<script>steal()</script>` then runs as actual JavaScript.

The defence is to convert each dangerous character to its HTML entity
(`<` → `&lt;`, `&` → `&amp;`, etc.) before injecting the data. Tiny
helper, big payoff.

```ts
function escapeHtml(s: string): string {
  return s.replace(/[&<>"']/g, c => ({
    '&': '&amp;',  '<': '&lt;', '>': '&gt;',
    '"': '&quot;', "'": '&#39;',
  }[c]!));
}
```

References:

- [OWASP — Cross Site Scripting (XSS)](https://owasp.org/www-community/attacks/xss/)
- [OWASP — XSS Prevention Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Cross_Site_Scripting_Prevention_Cheat_Sheet.html)
- [MDN — XSS](https://developer.mozilla.org/en-US/docs/Web/Security/Types_of_attacks#cross-site_scripting_xss)

For richer sanitisation (when you can't escape because the data
genuinely contains HTML), reach for [DOMPurify](https://github.com/cure53/DOMPurify).
For a small popup built from known-shape data, the five-character
escape we wrote is enough.

### Angular `@Input`

`@Input()` declares a property on a child component that the parent
can populate via a template binding.

```ts
// child component
@Component({ selector: 'app-greeting', template: `Hi, {{name}}!` })
export class GreetingComponent {
  @Input({ required: true }) name!: string;
}

// parent template
<app-greeting [name]="user.firstName"></app-greeting>
```

The `{ required: true }` flag makes Angular fail compilation if the
parent forgets to provide the value — useful for inputs that have no
sensible default.

References:

- [Angular — Inputs](https://angular.dev/guide/components/inputs)
- [Angular — Component Communication](https://angular.dev/guide/components/inputs-outputs)

### Angular `ngOnChanges`

Lifecycle hook called whenever any `@Input` changes. Receives a
`SimpleChanges` object — a map of input name → `{ previousValue,
currentValue }`. Useful for "react when an input changes" without
re-doing all the work that happens in `ngOnInit`.

```ts
ngOnChanges(changes: SimpleChanges) {
  if (changes['lat']) {
    this.refreshMap();   // only when lat actually changed
  }
}
```

Reference: [Angular — Lifecycle hooks](https://angular.dev/guide/components/lifecycle)

## The code, walked through

### MiniMapComponent — rename input, document the contract

The `label` input was a plain string showing in the popup. Renamed to
`popupHtml` so the contract is unambiguous, with documentation that
warns about XSS at the call site.

```ts
/**
 * HTML string shown inside the marker's popup when clicked.
 *
 * SECURITY NOTE: this string is rendered as raw HTML by Leaflet, so
 * any unescaped < > & " ' characters would be interpreted as markup.
 * Callers are responsible for escaping any user-provided data before
 * passing it in.
 */
@Input() popupHtml?: string;
```

The lifecycle handler now re-binds the popup if the content changes,
even when the coordinates are unchanged:

```ts
ngOnChanges(changes: SimpleChanges): void {
  if (this.map && (changes['lat'] || changes['lng'])) {
    // ... pan to new coordinates
  }
  if (this.map && this.marker && changes['popupHtml']) {
    if (this.popupHtml) {
      this.marker.bindPopup(this.popupHtml);
    } else {
      this.marker.unbindPopup();
    }
  }
}
```

### EmployeeDetailComponent — build the HTML safely

The popup HTML is built in the parent component (where the data is)
and passed down via `[popupHtml]`. We use `escapeHtml` on every piece
of data that's interpolated into the string.

```ts
buildOfficePopup(office: Office): string {
  const e = (s: string | null | undefined) => this.escapeHtml(s);
  const officeRoute = `/offices/${encodeURIComponent(office.officeCode)}`;

  return `
    <div style="min-width: 180px;">
      <strong>${e(office.city)} office</strong><br>
      ${e(office.addressLine1)}<br>
      ${office.addressLine2 ? `${e(office.addressLine2)}<br>` : ''}
      ${office.state ? `${e(office.state)}, ` : ''}${e(office.country)}<br>
      <span style="color: rgba(0,0,0,0.6);">📞 ${e(office.phone)}</span><br>
      <a href="${officeRoute}">View office details →</a>
    </div>
  `;
}
```

Three things to notice:

1. We alias `escapeHtml` as `e` to keep each interpolation short and readable.
2. `encodeURIComponent` is the URL-equivalent of HTML escaping — it
   protects against malicious data showing up inside a URL.
3. The conditional fragments use ternaries that produce empty strings
   when the source field is missing, so the HTML stays valid.

### Template — one input change

```html
<app-mini-map
  [lat]="o.lat"
  [lng]="o.lng"
  [popupHtml]="buildOfficePopup(o)">
</app-mini-map>
```

The template calls the method on every change-detection pass. That's
fine because `ngOnChanges` in the child only acts when the *value*
actually differs.

## How to test

1. Save and let the dev server hot-reload.
2. Open any employee's detail page (`/employees/1088` is William Patterson).
3. Click the blue map pin.
4. The popup should show the structured card.
5. Click "View office details →" — currently 404s because we haven't
   built that route yet. That confirms the link is being rendered as
   real markup.

## What you just learned

- **How to use template literals** to build readable HTML strings.
- **Why HTML escaping matters** and how to do it with five lines of code.
- **How `@Input` flows data into a child component**, and how to
  document the contract so other developers see warnings inline.
- **How `ngOnChanges` reacts to specific input changes** without
  conflating them with the initial setup that lives in `ngOnInit`.

## Study materials

The official references for each topic, plus one or two "narrative"
resources for those who prefer reading explanations to docs.

### Angular components

- [Angular — Components overview](https://angular.dev/guide/components)
- [Angular — Inputs](https://angular.dev/guide/components/inputs)
- [Angular — Lifecycle hooks](https://angular.dev/guide/components/lifecycle)
- [Angular Tutorial: Tour of Heroes](https://angular.dev/tutorials/tour-of-heroes) — a hands-on walkthrough

### TypeScript / JavaScript

- [MDN — Template literals](https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Template_literals)
- [MDN — encodeURIComponent](https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/encodeURIComponent)
- [TypeScript Handbook](https://www.typescriptlang.org/docs/handbook/intro.html) — official reference

### Web security

- [OWASP — XSS Prevention Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Cross_Site_Scripting_Prevention_Cheat_Sheet.html)
- [PortSwigger Academy — XSS](https://portswigger.net/web-security/cross-site-scripting) — interactive lessons (free)
- [DOMPurify](https://github.com/cure53/DOMPurify) — when you genuinely need to allow some HTML

### Leaflet

- [Leaflet — Quick Start](https://leafletjs.com/examples/quick-start/)
- [Leaflet — `bindPopup` reference](https://leafletjs.com/reference.html#marker-bindpopup)
- [Leaflet — Popup options](https://leafletjs.com/reference.html#popup-option)
