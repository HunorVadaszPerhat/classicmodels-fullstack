# Feature 09 — Profile photo uploads

## What we built

Each employee can now have a profile photo. The user uploads via a
button on the edit form (JPEG or PNG, up to 5MB), the backend stores
it on the filesystem, and the frontend displays it on the detail page
and in the form. Employees without a photo show a generic person-icon
placeholder. On the edit form an X-button overlay appears at the
bottom-right of the photo for one-click removal.

The interesting bit isn't the upload itself — it's *displaying* the
photo. Our endpoints are protected by JWT auth, but the browser
doesn't add Authorization headers to plain `<img src="...">`
requests. We work around it by fetching the photo as a Blob via
HttpClient (which our interceptor decorates with the bearer token)
and converting it to an object URL the `<img>` can render locally.

Files touched:

- `classicmodels-backend/src/main/java/.../photo/PhotoStorageService.java` (new)
- `classicmodels-backend/src/main/java/.../controller/EmployeeController.java`
- `classicmodels-backend/src/main/resources/application.yml`
- `classicmodels-ui/src/app/employees/employee-photo.component.ts` (new)
- `classicmodels-ui/src/app/employees/employee.service.ts`
- `classicmodels-ui/src/app/employees/employee-detail.component.{html,ts}`
- `classicmodels-ui/src/app/employees/employee-form.component.{html,ts}`

## Why this is worth learning

Three concepts converge. **Multipart file uploads** — how
`multipart/form-data` requests are built (browser side) and parsed
(Spring side). **Filesystem storage** as the simplest persistence
mode for binary blobs, plus the trade-offs vs DB blobs and S3.
**Authenticated images and object URLs** — the workaround for "the
browser won't put my JWT on `<img>` requests."

## Background

### Multipart uploads

When the browser uploads a file, it doesn't send the bytes as the
request body raw — it wraps them in a `multipart/form-data` envelope
that can carry multiple named parts (form fields + files) in one
request. The body looks roughly like:

```
------BOUNDARY
Content-Disposition: form-data; name="file"; filename="photo.jpg"
Content-Type: image/jpeg

<binary bytes here>
------BOUNDARY--
```

You don't write that yourself. On the browser side:

```ts
const body = new FormData();
body.append('file', file);
this.http.post(url, body);
```

Angular's HttpClient detects FormData and sets the `Content-Type:
multipart/form-data; boundary=...` header automatically, with the
right boundary string.

On the Spring side:

```java
@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
public ResponseEntity<Void> upload(
        @RequestParam("file") MultipartFile file) {
    file.getInputStream();   // stream the bytes
    file.getContentType();   // "image/jpeg"
    file.getOriginalFilename();
    file.getSize();
    ...
}
```

`MultipartFile` is Spring's wrapper. The `@RequestParam("file")` name
must match the part name on the client side.

References:

- [MDN — multipart/form-data](https://developer.mozilla.org/en-US/docs/Web/HTTP/Methods/POST#multipartform-data)
- [Spring docs — `MultipartFile`](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/web/multipart/MultipartFile.html)
- [Baeldung — Spring file upload](https://www.baeldung.com/spring-file-upload)

### Filesystem vs DB blob vs object storage

Three places binary data can live:

| Location       | Pros                              | Cons                          |
|----------------|-----------------------------------|-------------------------------|
| Filesystem     | Streaming, simple, fast           | Not portable across servers   |
| DB BLOB        | Backups in one place, transactional | Bloated backups, slow on retrieval |
| Object storage | Durable, portable, cheap, scalable | External dependency, network IO |

We picked filesystem because it's the simplest learning option.
Production-grade for any multi-server deployment is object storage
(S3, MinIO, GCS). The migration path is straightforward — same
`PhotoStorageService` interface, different implementation behind it.

### Authenticated images and object URLs

The browser sends a request when it sees `<img src="...">`. That
request:

- Has its own headers (Accept, Cache-Control, etc.).
- Includes cookies bound to the URL's domain.
- **Does NOT include `Authorization` headers added by your app's
  HTTP layer.**

So a JWT-protected image endpoint can't be used directly as an `<img>`
src — the browser's request gets a 401 because the token isn't there.

The workaround:

1. Fetch the image with `HttpClient`, which DOES include the
   Authorization header (your interceptor adds it).
2. The response body is a `Blob` of the image bytes.
3. `URL.createObjectURL(blob)` returns a URL like
   `blob:http://localhost:4200/abc-123-def-...` that points at the
   in-memory Blob.
4. Set the `<img>` src to that URL — the browser renders it from
   memory, no further network call.
5. Call `URL.revokeObjectURL(url)` when done so the Blob can be
   garbage-collected.

```ts
this.service.fetchPhoto(id).subscribe(blob => {
  this.photoUrl.set(URL.createObjectURL(blob));
});
// Later, on destroy:
URL.revokeObjectURL(this.photoUrl()!);
```

Step 5 matters. Object URLs hold a reference to the Blob bytes; if
you create one per page-view and never revoke, your memory grows
unboundedly until the user reloads. Easy to miss in a long-running
SPA.

Alternative approaches:

- **Make the photo endpoint public.** Simpler client code, but anyone
  can guess `/employees/N/photo` and download the photos. Often
  acceptable for non-sensitive data.
- **Token in query string.** `<img src="/photo?token=...">`. Logs the
  token in server access logs, browser history, and referer headers
  — usually a poor idea.
- **Cookie auth.** Cookies are auto-sent on `<img>` requests. Works
  cleanly if you're using session cookies; doesn't help with JWT in
  Authorization header.

References:

- [MDN — `URL.createObjectURL`](https://developer.mozilla.org/en-US/docs/Web/API/URL/createObjectURL_static)
- [MDN — Authorization header](https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Authorization)
- [MDN — Blob](https://developer.mozilla.org/en-US/docs/Web/API/Blob)

### `@PostConstruct` for initialisation

Spring runs `@PostConstruct` methods after a bean is fully
constructed and dependency injection is complete. Useful for
"do this once, when the bean comes up" actions like
`Files.createDirectories(...)`:

```java
@PostConstruct
void init() throws IOException {
    Files.createDirectories(baseDir);
}
```

Throwing here aborts the application startup — exactly what you want
when the configured upload directory is unwriteable.

References:

- [Jakarta EE — `@PostConstruct`](https://jakarta.ee/specifications/annotations/2.1/jakarta-annotations-spec-2.1.html)
- [Baeldung — Spring `@PostConstruct`](https://www.baeldung.com/spring-postconstruct-predestroy)

## The code, walked through

### Storage service — three small methods

```java
public String save(int employeeId, MultipartFile file) throws IOException {
    String ext = extensionFor(file);
    delete(employeeId);   // ensure 1 photo per id
    Path target = baseDir.resolve(employeeId + "." + ext);
    Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
    return ext;
}

public Optional<Path> find(int employeeId) {
    for (String ext : ALLOWED_EXTENSIONS) {
        Path candidate = baseDir.resolve(employeeId + "." + ext);
        if (Files.exists(candidate)) return Optional.of(candidate);
    }
    return Optional.empty();
}

public void delete(int employeeId) {
    for (String ext : ALLOWED_EXTENSIONS) {
        Files.deleteIfExists(baseDir.resolve(employeeId + "." + ext));
    }
}
```

The pattern: filename is the employee id with the appropriate
extension. `find` doesn't need to know which extension was used — it
tries each. `delete` is idempotent (missing files are silent).
Trade-off vs storing the path in the DB: simpler, but you can't
trivially answer "which employees have photos?" — you'd need to
scan the directory.

### EmployeePhotoComponent — Blob → object URL

```ts
loadPhoto(): void {
  this.revokeCurrent();
  this.service.fetchPhoto(this.employeeId).pipe(
    catchError(() => of(null)),  // 404 = no photo, fall through
  ).subscribe(blob => {
    if (blob == null) {
      this.photoUrl.set(null);
      return;
    }
    this.photoUrl.set(URL.createObjectURL(blob));
  });
}

revokeCurrent(): void {
  const url = this.photoUrl();
  if (url) {
    URL.revokeObjectURL(url);
    this.photoUrl.set(null);
  }
}
```

`revokeCurrent` runs both before loading a new photo (so we don't
leak the previous one) and on `ngOnDestroy`. The two failure paths
(network down, no photo on file) both produce a null Blob → the
template falls through to the placeholder icon.

### File-input pattern in the form

```html
<input #fileInput type="file"
       accept="image/jpeg,image/png"
       hidden
       (change)="onPhotoSelected($event)" />
<button mat-stroked-button type="button" (click)="fileInput.click()">
  <mat-icon>cloud_upload</mat-icon> Upload photo
</button>
```

The `#fileInput` template-reference variable gives the button's
click handler access to the input's DOM node so it can call its
`.click()` method programmatically. This is the standard pattern for
"keep the file picker but show a styled button" — `<input type="file">`
itself is a pain to style.

`accept` is a hint to the OS file dialog; the user can still pick
anything but the dialog defaults to filtering to images.

After upload, we set `input.value = ''` so picking the *same* file
again still fires the (change) event. Browsers de-dupe identical
selections by default.

### The remove button overlay

Adding "remove" to the same component (rather than the parent) keeps
the photo's UX self-contained. Two new pieces of API:

- `[deletable]` input — opt-in flag the parent sets when removal is
  appropriate (true on the edit form, false everywhere else).
- An internal `removePhoto()` method calls `deletePhoto` on the
  service, then revokes the local object URL so the placeholder
  renders.

The button is a small circular `<button>` overlaid on the photo via
`position: absolute` inside a `position: relative` frame:

```css
.frame {
  position: relative;
  width: var(--photo-size, 96px);
  height: var(--photo-size, 96px);
}
.remove-btn {
  position: absolute;
  right: -4px;
  bottom: -4px;
  width: 24px; height: 24px;
  border-radius: 50%;
  ...
}
```

Negative offsets push the button outside the photo's edge so the
rounded `border-radius: 50%` mask doesn't clip it. The button is
only rendered when `deletable && photoUrl()` — no point letting the
user click X on a placeholder.

The error path is deliberately silent: the most likely failure
("photo wasn't there") and the success ("photo deleted") have the
same desired local state (no photo). So we drop the local URL in
both branches and don't pop a dialog.

## How to test

1. Restart the backend. Look for `Employee-photo storage initialised
   at /path/to/uploads/employee-photos` in the logs.
2. Sign in. Navigate to **Employees → Diane Murphy → Edit**.
3. Above the form fields you should see a placeholder icon and an
   **Upload photo** button.
4. Click the button. Pick any JPEG or PNG.
5. After ~1 second the placeholder is replaced with the photo.
6. Save the form (or Cancel — the photo upload is independent).
7. Navigate to Diane's detail page. Her new photo appears as the
   card avatar.
8. In the file system, look at `<project-root>/uploads/employee-photos/`
   — you should see `1002.jpg` (or `.png`).

API check via curl:

```bash
# Upload
curl -X POST http://localhost:9090/api/v1/employees/1002/photo \
  -H "Authorization: Bearer $TOKEN" \
  -F "file=@./test.jpg"

# Fetch
curl http://localhost:9090/api/v1/employees/1002/photo \
  -H "Authorization: Bearer $TOKEN" \
  -o downloaded.jpg

# Delete
curl -X DELETE http://localhost:9090/api/v1/employees/1002/photo \
  -H "Authorization: Bearer $TOKEN"
```

## What you just learned

- **Multipart upload** wire format and how Spring's `@RequestParam
  MultipartFile` parses it.
- **`@PostConstruct`** for one-time initialisation that needs the
  bean to be fully wired.
- **Filesystem storage** with `java.nio.file.{Path,Files}` —
  `Files.copy(stream, path, REPLACE_EXISTING)` for atomic writes.
- **The auth-image gotcha** — `<img src>` doesn't carry your JWT, so
  fetch as Blob + use `URL.createObjectURL`.
- **Memory hygiene** with `URL.revokeObjectURL` to prevent slow leaks.
- **The hidden-file-input pattern** for styled upload UX.

## Study materials

### Multipart uploads

- [MDN — multipart/form-data](https://developer.mozilla.org/en-US/docs/Web/HTTP/Methods/POST#multipartform-data)
- [Spring docs — Multipart resolver](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-servlet/multipart.html)
- [Baeldung — Spring multipart upload](https://www.baeldung.com/spring-file-upload)
- [Angular HTTP — Sending data](https://angular.dev/guide/http/making-requests) — the `FormData` snippets

### Filesystem operations in Java

- [Oracle — `java.nio.file` overview](https://docs.oracle.com/javase/tutorial/essential/io/fileio.html)
- [Oracle — `Files` API](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/file/Files.html)
- [Baeldung — Java NIO file tutorial](https://www.baeldung.com/java-nio-2-file-api)

### Object URLs and Blobs

- [MDN — Using object URLs](https://developer.mozilla.org/en-US/docs/Web/API/File_API/Using_files_from_web_applications#using_object_urls)
- [MDN — Blob](https://developer.mozilla.org/en-US/docs/Web/API/Blob)
- [MDN — URL.createObjectURL](https://developer.mozilla.org/en-US/docs/Web/API/URL/createObjectURL_static)

### Production-grade upload concerns (out of scope, but worth knowing)

- [Mozilla — File-upload security checklist](https://infosec.mozilla.org/guidelines/web_security#content-security-policy) — search "file upload"
- [OWASP — File upload cheat sheet](https://cheatsheetseries.owasp.org/cheatsheets/File_Upload_Cheat_Sheet.html)
- [AWS — S3 PutObject](https://docs.aws.amazon.com/AmazonS3/latest/API/API_PutObject.html) — when you outgrow the filesystem
