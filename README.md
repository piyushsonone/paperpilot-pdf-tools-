# PaperPilot — PDF tools

A Java 17 + Spring Boot + Apache PDFBox PDF utility web app with an original interface inspired by the general workflow of online PDF tool suites. It is not affiliated with or a copy of iLovePDF branding/assets.

## Included tools

- **Browser preview:** select a PDF to render page 1, navigate pages, and use page thumbnails before processing. Preview is rendered locally in the browser using PDF.js from a CDN.
- **Compress PDF:** Light, Recommended, and Extreme modes; optional target size in MB. The app first tries a structure/metadata save that preserves text, then uses a lower-resolution raster copy if needed. Strong compression may make text non-selectable and may still not hit the exact requested size.
- **Merge PDF:** combine 2–20 PDFs in the order selected.
- **Split PDF:** download each page as a separate PDF inside a ZIP.
- **Extract pages:** extract page numbers and ranges, e.g. `1,3,5-7`.
- **Rotate PDF:** rotate all pages by 90°, 180°, or 270°.
- **Organize PDF:** remove pages and optionally specify a new order, e.g. `3,1,2`.
- **Remove pages:** remove specific page numbers or ranges.
- **Watermark PDF:** add text watermark.
- **Page numbers:** add page numbers at bottom left, center, or right.
- **Protect PDF:** encrypt with a password.

The **Resize PDF** utility has been removed as requested. This is a working starter, not a feature-for-feature replacement for every iLovePDF service. Conversions (Word/Excel/PowerPoint), OCR, signatures, redact, repair, AI tools, and full text editing are not included.

## Requirements

- JDK 17 or later
- Maven 3.8+
- Internet access on the first build to download Maven dependencies. PDF preview also loads PDF.js from a CDN.

## Run locally on Windows PowerShell

Open PowerShell in the folder that contains `pom.xml`, then run:

```powershell
mvn spring-boot:run
```

Open http://localhost:8080. Keep the terminal open while using the app.

To build a jar:

```powershell
mvn clean package
java -jar target/pdf-toolkit-0.0.1-SNAPSHOT.jar
```

## API

- `POST /api/pdf/merge` — multipart `files` (repeat for each PDF)
- `POST /api/pdf/compress` — multipart `file`, optional `level` (`light`, `recommended`, `strong`), optional `targetMb`
- `POST /api/pdf/edit` — multipart `file`, optional `deletePages`, `rotateDegrees`, `pageOrder`
- `POST /api/pdf/split` — multipart `file`, returns a ZIP
- `POST /api/pdf/extract` — multipart `file`, `pages` (e.g. `1,3,5-7`)
- `POST /api/pdf/watermark` — multipart `file`, `text`, optional `fontSize`
- `POST /api/pdf/page-numbers` — multipart `file`, optional `position`
- `POST /api/pdf/protect` — multipart `file`, `password`

Page numbers are 1-based. Current per-file limit is 30 MB; request limits are configured in `application.properties`.

## Deploy to Render using Docker

1. Push the contents of this project folder to a GitHub repository.
2. In Render, create a new Web Service and connect the repository.
3. Choose Docker runtime (the included Dockerfile builds with JDK 17).
4. Deploy and test the public URL.

The included `render.yaml` is a starter blueprint. Confirm current hosting limits before public launch.

## Before production

- Files are processed in memory and are not intentionally persisted, but this app has not had a security audit.
- Add rate limiting, abuse protection, authentication if needed, privacy/retention documentation, monitoring, and tests before handling public traffic.
- Encrypted, corrupt, very large, or unusual PDFs may fail. Compression target size is approximate and can affect text selection/search.
- PDF.js is loaded from a CDN; self-host it if you need preview functionality without third-party CDN dependency.
