# Windows HTTP Range Server Design

## Goal

Provide read-only HTTP access to two existing media directories on a Windows
machine. Clients on the trusted local network must be able to browse directory
listings and read arbitrary byte ranges from media files.

## Confirmed constraints

- The server runs on Windows.
- Access is limited to the local network.
- Transport is plain HTTP; HTTPS is intentionally out of scope.
- Authentication is intentionally omitted because HTTP Basic Authentication
  would not protect credentials on an unencrypted connection.
- The service listens on TCP port `8080`.
- Directory listings are enabled.
- Media files must never be modified by the server.
- `I:\MiddleDir` and `G:\pik` both exist and are local fixed-disk directories.
- TCP port `8080` was unoccupied during design validation.

## Architecture

Caddy runs as an automatically started Windows service and listens on all local
interfaces at TCP port `8080`. Windows Defender Firewall permits inbound traffic
to that port only from `LocalSubnet`.

The URL namespace is:

| URL prefix | Windows directory |
| --- | --- |
| `/middle/` | `I:\MiddleDir` |
| `/pik/` | `G:\pik` |

For example:

```text
http://<server-lan-address>:8080/middle/movie.mp4
http://<server-lan-address>:8080/pik/example/image.jpg
```

Each prefix provides Caddy's directory browser. Requests for files are handled
by Caddy's static file server, including `HEAD`, byte-range requests, partial
responses, entity metadata, `404 Not Found`, and `416 Range Not Satisfiable`.
Requests outside the two prefixes return `404`.

## Components

### Caddy

- Use the official Windows AMD64 Caddy binary.
- Store the executable under `C:\Program Files\Caddy`.
- Store the Caddyfile and operational data under `C:\ProgramData\Caddy`.
- Run Caddy through the Windows Service Control Manager with automatic startup.
- Disable automatic HTTPS by using an explicit HTTP site address.
- Do not enable response compression for media files.
- Enable structured access logs with rotation under
  `C:\ProgramData\Caddy\logs`.

### Filesystem access

- Caddy receives only the permissions needed to traverse and read the two media
  directories.
- Caddy configuration exposes the directories through distinct URL prefixes;
  it never exposes a drive root.
- No upload, delete, rename, WebDAV, or write endpoint is present.

### Network access

- Listen on TCP port `8080`.
- Create a narrowly scoped Windows Defender Firewall rule whose remote address
  is `LocalSubnet`.
- Do not create or retain a router port-forward for TCP `8080`.
- Clients use the server's stable LAN address or LAN hostname.

## Data flow

1. A local-network client requests a directory or media URL.
2. Windows Defender Firewall rejects requests not originating from the local
   subnet.
3. Caddy maps `/middle/` or `/pik/` to the corresponding directory.
4. Directory requests receive a generated listing.
5. File requests with a valid `Range` header receive `206 Partial Content` and
   the requested bytes.
6. Invalid paths receive `404`; unsatisfiable ranges receive `416`.

## Security boundary

The trusted LAN is the only security boundary. Directory names, filenames, and
media contents are visible to any device allowed onto that LAN. Plain HTTP
provides neither confidentiality nor peer authentication. If the service later
needs guest-Wi-Fi, VPN, or public access, this design must be revised before the
firewall scope is widened.

## Validation

Deployment is accepted only when all of the following pass:

1. Caddy validates the Caddyfile successfully.
2. The Windows service is running and configured for automatic startup.
3. The firewall rule is limited to `LocalSubnet`.
4. Both `/middle/` and `/pik/` return directory listings from another LAN
   device.
5. A request for bytes `0-1023` returns status `206`, a valid `Content-Range`,
   and exactly 1024 bytes.
6. An unsatisfiable range returns `416`.
7. A path outside both configured prefixes returns `404`.
8. No write, upload, delete, or rename operation is exposed.

## Rollback

Stop and remove the Caddy Windows service, remove the dedicated firewall rule,
and delete the Caddy installation/configuration directories. Media files are
not changed by deployment or rollback.
