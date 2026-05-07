# Feature 15 — OAuth2 with Google Sign-In

## What we built

The login page now has a "Sign in with Google" button alongside the
username/password form. Clicking it kicks off the standard OAuth2
authorization-code flow: browser → backend → Google → backend → SPA.
After Google authenticates the user, the backend mints one of *our
own* JWTs (the same kind issued by `POST /auth/login`) and redirects
the browser back to the SPA with the token in a URL fragment. The
SPA captures it, stores it in localStorage, and the rest of the app
treats the user identically to a username-password sign-in.

The Spring Security side is mostly configuration — the framework
already implements the OAuth2 client filters and state handling. The
piece we wrote ourselves is the **success handler** that bridges
"Spring's OAuth2 Authentication" to "our app's JWT tokens." Without
it, Spring would default to a session-based login that doesn't fit a
stateless SPA.

Files touched:

- `classicmodels-backend/pom.xml` — `spring-boot-starter-oauth2-client`
- `classicmodels-backend/src/main/resources/application.yml` —
  Google client registration, success-redirect path
- `classicmodels-backend/src/main/java/.../security/OAuth2LoginSuccessHandler.java` (new)
- `classicmodels-backend/src/main/java/.../security/SecurityConfig.java` —
  `oauth2Login(...)`, permit `/oauth2/**` + `/login/oauth2/**`
- `classicmodels-ui/src/app/auth/auth.service.ts` — `loginWithToken(token)`
- `classicmodels-ui/src/app/auth/oauth2-success.component.ts` (new)
- `classicmodels-ui/src/app/auth/login.component.ts` — Google button + styles
- `classicmodels-ui/src/app/app.routes.ts` — `/login/oauth2/success` route

## Why this is worth learning

OAuth2 is the auth protocol that powers "Sign in with X" everywhere.
Once you've implemented one provider end-to-end, every other one
fits the same template: GitHub, Microsoft, Apple, Facebook, Okta,
Auth0, Keycloak. The differences are which scopes to ask for and
which user-info field carries the email.

Three concepts come together.

**The authorization-code flow** — the standard "delegate auth to
another site, then come back with a verifiable proof" dance. Six
HTTP redirects, each doing one specific thing. Worth understanding
in detail, because security depends on knowing *why* there are six
and what each one prevents.

**Bridging two auth systems** — Spring Security has its own
representation of an authenticated user (`Authentication` object,
session-bound). Our app uses JWTs. The success handler is a
translator: take Google's authentication, repackage as our JWT.

**The redirect-URI security model** — the most subtle piece.
"Where can the auth provider send the user back?" is a critical
config value, registered with the provider, and the source of more
OAuth2 vulnerabilities than anything else.

## Background

### The OAuth2 authorization-code flow

The complete flow has six steps:

```
1. SPA → Backend:        GET /oauth2/authorization/google
2. Backend → Browser:    302 to https://accounts.google.com/o/oauth2/v2/auth?...
3. Browser → Google:     User logs in, consents
4. Google → Browser:     302 to <our-redirect-uri>?code=AUTH_CODE&state=...
5. Backend → Google:     POST /token with code, client-id, client-secret  → ID token + access token
6. Backend → SPA:        302 to /login/oauth2/success#token=<JWT>
```

The two key handshakes are step 2 (we send the user to Google) and
step 5 (we exchange the auth code for tokens, server-to-server,
proving we're really us via the client secret).

A few subtle things this flow gets right:

- **The user's password never reaches our app.** It's typed into
  Google's domain, on a TLS-secured page Google controls.
- **Our client secret never reaches the browser.** Step 5 is a
  server-to-server call. The browser can't impersonate us because it
  doesn't have the secret.
- **`state` parameter** prevents CSRF: a random nonce we send to
  Google in step 2 and verify on the way back in step 4. If an
  attacker tricks a user into completing the flow on their behalf,
  the state mismatch detects it.
- **The auth code is one-time use** and short-lived. Even if it
  leaks (e.g. via a referrer header), it's worthless without the
  client secret to redeem it.

References:

- [RFC 6749 — The OAuth 2.0 Authorization Framework](https://datatracker.ietf.org/doc/html/rfc6749)
- [OAuth.net — "Authorization Code" flow](https://www.oauth.com/oauth2-servers/server-side-apps/authorization-code/)
- [Aaron Parecki — OAuth 2.0 Simplified](https://www.oauth.com/) — the canonical practitioner explainer

### OpenID Connect (OIDC) and ID tokens

OAuth2 is an *authorization* protocol — "let app X access resource Y
on behalf of user Z." OpenID Connect (OIDC) is an *authentication*
layer on top — "tell me who this user is."

The practical difference: OIDC adds a third token to the response,
the **ID token** — a JWT that asserts identity claims (email, name,
sub). Google issues an ID token whenever you request the `openid`
scope, which we do. The ID token is what tells us "the user signing
in is `alice@example.com`."

We could use the ID token directly as our app's token (instead of
minting a new JWT), but mixing token issuers complicates everything
downstream — we'd have to swap our JWT validation for "ours OR
Google's, and route by issuer." Easier to convert at the boundary.

References:

- [OpenID Connect Core 1.0](https://openid.net/specs/openid-connect-core-1_0.html)
- [Google Identity — OpenID Connect](https://developers.google.com/identity/openid-connect/openid-connect)
- [Auth0 — JWT vs ID token](https://auth0.com/docs/secure/tokens/id-tokens)

### Redirect URIs — the security model

When you register an OAuth2 client with a provider, you tell the
provider the exact URLs it's allowed to redirect users back to.
Google rejects redirects to anything not on that list.

Why it matters: if redirect-URI matching were lax, an attacker could
build a malicious app that redeems your auth code at their site,
impersonating you. The exact-match rule is what prevents that.

Common gotchas:

- Trailing slashes matter. `http://localhost:9090/cb` and
  `http://localhost:9090/cb/` are different URLs to most providers.
- Localhost is allowed for dev (Google permits `http://localhost:*`
  even though TLS is normally required) but **only on the loopback
  interface**.
- The redirect URI must be the URL the *browser* hits, which in our
  setup goes through the Vite dev proxy: register
  `http://localhost:4200/api/v1/login/oauth2/code/google`, not the
  backend's `:9090` URL — the browser never visits the backend
  directly.

References:

- [RFC 6749 §10.6 — Authorization Code Redirection URI Manipulation](https://datatracker.ietf.org/doc/html/rfc6749#section-10.6)
- [Google — Redirect URI validation](https://developers.google.com/identity/protocols/oauth2/web-server#redirecturi)
- [OWASP — OAuth Redirect URI manipulation](https://cheatsheetseries.owasp.org/cheatsheets/OAuth_Cheat_Sheet.html)

### URL fragment vs. query string for the token

The success handler redirects to
`/login/oauth2/success#token=<jwt>`. Why fragment, not
`?token=<jwt>`?

| | Query parameter | URL fragment |
|---|---|---|
| Sent to server in HTTP request | **Yes** | No |
| Visible in browser history | Yes | Yes |
| Visible in Referer header on next click | Yes | No |
| Logged by the SPA host server | Yes | **No** |
| Accessible from JavaScript | `URLSearchParams` | `window.location.hash` |

For a sensitive token, fragment leaks in fewer places. Fragments are
purely client-side — the server hosting the SPA doesn't see them in
its access logs. SPAs that survive the redirect by reloading don't
accidentally include fragments in subsequent requests.

The serious alternative is **HttpOnly cookies**: instead of putting
the token in the URL at all, set it as a cookie that JavaScript
can't read. Most secure (XSS doesn't leak it), but reintroduces CSRF
considerations and complicates the API client.

References:

- [MDN — `Location.hash`](https://developer.mozilla.org/en-US/docs/Web/API/Location/hash)
- [OWASP — Token Storage](https://cheatsheetseries.owasp.org/cheatsheets/HTML5_Security_Cheat_Sheet.html#sessionstorage)
- [Auth0 — Token storage trade-offs](https://auth0.com/docs/secure/security-guidance/data-security/token-storage)

### Spring Security's OAuth2 Client

The `spring-boot-starter-oauth2-client` dependency drops in a
ready-to-use OAuth2 client. The auto-configuration:

- Reads client registrations from
  `spring.security.oauth2.client.registration.*` in your config.
- Exposes `/oauth2/authorization/{registrationId}` to start a flow.
- Handles the callback at `/login/oauth2/code/{registrationId}`.
- Maps the user-info response to a Spring Security `OAuth2User`.
- Stores `state` and PKCE values in the HTTP session (yes, even in
  our otherwise stateless app — the OAuth2 filter chain creates a
  session for state tracking).
- Plugs the `OAuth2User` into the standard `Authentication` slot in
  `SecurityContextHolder`.

Spring even ships `CommonOAuth2Provider` enums for popular providers
(Google, GitHub, Facebook, Okta) — you only spell out client-id +
client-secret + scopes; the URLs are pre-configured.

References:

- [Spring Security — OAuth2 Login](https://docs.spring.io/spring-security/reference/servlet/oauth2/login/index.html)
- [Spring Boot — OAuth2 client properties](https://docs.spring.io/spring-boot/reference/web/spring-security.html#web.security.oauth2.client)
- [Baeldung — Spring Security OAuth2 Login](https://www.baeldung.com/spring-security-5-oauth2-login)

## The code, walked through

### Backend — the success handler

```java
@Override
public void onAuthenticationSuccess(HttpServletRequest req,
                                    HttpServletResponse res,
                                    Authentication auth) throws IOException {
    OAuth2User principal = (OAuth2User) auth.getPrincipal();
    String email = principal.getAttribute("email");
    List<String> roles = List.of("ROLE_USER");
    String token = jwt.issue(email, roles);

    String encoded = URLEncoder.encode(token, StandardCharsets.UTF_8);
    res.sendRedirect(successRedirect + "#token=" + encoded);
}
```

The whole bridge fits in fifteen lines. We extract the email Google
returned, mint a JWT with the same `JwtService` used for
username/password login, and 302 to the SPA with the token in the
fragment.

The `successRedirect` is `${OAUTH2_SUCCESS_REDIRECT:/login/oauth2/success}` —
configurable so deployments behind different SPA URLs work without
code changes.

### Backend — `oauth2Login(...)` config

```java
.oauth2Login(oauth -> oauth
    .successHandler(oauth2SuccessHandler)
    .failureHandler((req, res, e) ->
            res.sendError(HttpServletResponse.SC_UNAUTHORIZED,
                          "OAuth2 login failed: " + e.getMessage()))
)
```

That's the entire wiring. Spring's auto-config does the rest: the
filters, the state nonce, the user-info fetch, the
`Authentication` population. We only override the success path
(because we want JWT, not session) and the failure path (so it
returns a clean 401 instead of bouncing to a default error page).

### Backend — permitting the OAuth2 paths

```java
.requestMatchers("/oauth2/**", "/login/oauth2/**").permitAll()
```

These URLs need to be reachable BEFORE the user is authenticated —
they're how anonymous users start the sign-in process. The default
"any request authenticated" rule would redirect them to /login,
breaking the flow.

### Frontend — the Google button

```html
<a mat-stroked-button
   class="google-btn full-width"
   href="/api/v1/oauth2/authorization/google">
  <span class="g-mark">G</span>
  Sign in with Google
</a>
```

Plain anchor, full-page navigation. We don't use `HttpClient` or
`Router` because the next step is a 302 to Google — the SPA is going
to be unloaded entirely. By the time the user comes back, the SPA
will boot up at `/login/oauth2/success` instead of `/login`.

### Frontend — the success component

```ts
const hash = window.location.hash.startsWith('#')
    ? window.location.hash.substring(1)
    : window.location.hash;
const params = new URLSearchParams(hash);
const token = params.get('token');

this.auth.loginWithToken(token).subscribe({
  next: user => {
    if (user) this.router.navigateByUrl('/');
  },
});
```

Read the fragment, parse it like a query string (URLSearchParams
works on either), pull out the token, hand it to AuthService.
`loginWithToken` stores it in localStorage and calls `/auth/me` to
populate the canonical user info — same shape as a username/password
login from this point on.

### Frontend — `AuthService.loginWithToken`

```ts
loginWithToken(token: string): Observable<UserInfo | null> {
  localStorage.setItem(TOKEN_KEY, token);
  return this.refresh();
}
```

Three lines. Persist, then refresh. The `refresh()` call hits
`/auth/me` with the new token in the Authorization header (the
interceptor reads it from localStorage), which validates the JWT
and returns `{ username, roles }`.

## How to test

Without Google credentials configured the button will fail when
clicked — Google rejects "client unauthorized." That's the expected
state until you set up an OAuth client.

### Setting up Google credentials

1. Go to [Google Cloud Console](https://console.cloud.google.com/).
2. Create a project (or pick an existing one).
3. APIs & Services → **OAuth consent screen** → External, fill in
   the basics (app name, support email).
4. APIs & Services → **Credentials** → "Create credentials" →
   "OAuth client ID" → "Web application."
5. Authorized redirect URIs — **add**:
   `http://localhost:4200/api/v1/login/oauth2/code/google`
   (this is the URL the browser hits, then the Vite proxy forwards
   to the backend at :9090.)
6. Save. Google gives you a **Client ID** and **Client Secret**.
7. Set them in your shell before starting the backend:

```sh
export GOOGLE_CLIENT_ID=xxxxxxxx.apps.googleusercontent.com
export GOOGLE_CLIENT_SECRET=GOCSPX-yyyyyyyy
./mvnw spring-boot:run
```

(Or in your IDE's run configuration as environment variables.)

### Using the button

1. Go to `/login`.
2. Click **Sign in with Google**.
3. You're redirected to Google. Pick an account, consent.
4. Google redirects you to `/login/oauth2/success#token=...`.
5. The SPA briefly shows a spinner ("Finishing sign-in…") and lands
   on `/`.
6. The toolbar shows your Google email as the signed-in user.

### Without credentials

The button still appears. Click it, and the backend forwards to
Google with `client_id=NOT_CONFIGURED`, which Google rejects with a
"client unauthorized" error page. The doc note above explains how
to fix it. The username/password path stays fully functional.

## What you just learned

- **The OAuth2 authorization-code flow** end-to-end, including why
  there are exactly six redirects.
- **OIDC vs. OAuth2** — `openid` scope, ID tokens, and the difference
  between authentication and authorization.
- **Spring Security `oauth2Login`** as a one-line wiring of a fairly
  complex protocol.
- **`AuthenticationSuccessHandler`** as the pluggable point that
  lets you bridge OAuth2 success to your own session/JWT scheme.
- **URL fragment vs. query parameter** as a security choice for
  passing tokens between origins.
- **Redirect URI exact-matching** as the cornerstone of OAuth2
  redirect security.
- **The role of `state` and PKCE** in defending the flow against
  CSRF and code-injection attacks.
- **Spring's `CommonOAuth2Provider`** as a shortcut for popular
  providers' endpoint URLs.

## Study materials

### OAuth 2.0 / OIDC fundamentals

- [Aaron Parecki — OAuth 2.0 Simplified](https://www.oauth.com/) — book + free site, the standard practitioner reference
- [RFC 6749 — OAuth 2.0 Authorization Framework](https://datatracker.ietf.org/doc/html/rfc6749)
- [RFC 6750 — OAuth 2.0 Bearer Token Usage](https://datatracker.ietf.org/doc/html/rfc6750)
- [OpenID Connect Core 1.0](https://openid.net/specs/openid-connect-core-1_0.html)
- [Okta Dev — OAuth 2.0 in plain English](https://developer.okta.com/blog/2017/06/21/what-the-heck-is-oauth)
- [Auth0 — Authentication API explorer](https://auth0.com/docs/api/authentication) — tinker with real flows

### PKCE and modern OAuth2

- [RFC 7636 — Proof Key for Code Exchange](https://datatracker.ietf.org/doc/html/rfc7636) — what protects public clients
- [OAuth Working Group — OAuth 2.1 draft](https://oauth.net/2.1/) — PKCE-mandatory consolidation
- [Aaron Parecki — Why mobile apps need PKCE](https://oauth.net/2/pkce/)

### Spring Security + OAuth2

- [Spring Security — OAuth2 Login](https://docs.spring.io/spring-security/reference/servlet/oauth2/login/index.html)
- [Spring Boot — OAuth2 client](https://docs.spring.io/spring-boot/reference/web/spring-security.html#web.security.oauth2.client)
- [Spring Security — OAuth2 Resource Server](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/index.html) — for accepting Google's tokens directly without minting our own
- [Baeldung — Spring Security 5 OAuth2 Login](https://www.baeldung.com/spring-security-5-oauth2-login)
- [Spring docs — `CommonOAuth2Provider`](https://docs.spring.io/spring-security/site/docs/current/api/org/springframework/security/config/oauth2/client/CommonOAuth2Provider.html)

### Provider-specific guides

- [Google — Web server apps OAuth 2.0](https://developers.google.com/identity/protocols/oauth2/web-server)
- [GitHub — Authorizing OAuth Apps](https://docs.github.com/en/apps/oauth-apps/building-oauth-apps/authorizing-oauth-apps)
- [Microsoft — OIDC tutorial](https://learn.microsoft.com/en-us/entra/identity-platform/v2-protocols-oidc)

### Token storage

- [OWASP — HTML5 Security Cheat Sheet (storage section)](https://cheatsheetseries.owasp.org/cheatsheets/HTML5_Security_Cheat_Sheet.html)
- [Auth0 — Token storage best practices](https://auth0.com/docs/secure/security-guidance/data-security/token-storage)
- [Hasura — Storing JWTs in cookies vs. localStorage](https://hasura.io/blog/best-practices-of-using-jwt-with-graphql) — the trade-off space
- [Stop using JWT for sessions — by Brock Allen](https://leastprivilege.com/2017/02/03/why-not-jwt/) — counterpoint worth reading

### Production hardening

- [Spring Security — Token introspection](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/opaque-token.html)
- [Spring Security — Session management](https://docs.spring.io/spring-security/reference/servlet/authentication/session-management.html) — concurrent sessions, fixation
- [Auth0 — Refresh tokens](https://auth0.com/docs/secure/tokens/refresh-tokens)
- [OWASP — JWT cheat sheet](https://cheatsheetseries.owasp.org/cheatsheets/JSON_Web_Token_for_Java_Cheat_Sheet.html)

### Self-hosted alternatives to Google

- [Keycloak](https://www.keycloak.org/) — open-source identity provider; same protocol as Google
- [Authentik](https://goauthentik.io/) — modern, also OSS
- [Ory Hydra](https://www.ory.sh/hydra/) — pure OAuth2/OIDC server
- [Dex](https://dexidp.io/) — federation-focused; bridges other IdPs
