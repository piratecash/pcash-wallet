# Third-party notices

The desktop package includes the following third-party components for QR scanning with a webcam.

| Component | License | Upstream |
|---|---|---|
| openpnp-capture (native camera library) | MIT | https://github.com/openpnp/openpnp-capture (built from https://github.com/piratecash/openpnp-capture) |
| openpnp-capture-java (Java binding) | MIT | https://github.com/openpnp/openpnp-capture-java (built from https://github.com/piratecash/openpnp-capture-java) |
| libjpeg-turbo (MJPEG decoding inside openpnp-capture on Linux) | BSD-3-Clause, IJG | https://github.com/libjpeg-turbo/libjpeg-turbo |
| JNA (Java Native Access) | Apache-2.0 | https://github.com/java-native-access/jna |
| Sparrow Wallet `WebcamService` (camera selection logic, ported and modified) | Apache-2.0 | https://github.com/sparrowwallet/sparrow |

The macOS and Linux packages include Tor from the Tor Expert Bundle 16.0.1 (https://www.torproject.org).

| Component | License | Upstream |
|---|---|---|
| tor 0.4.9.16 (this build reports itself as covered by GPL-3.0, text in `GPL-3.0.txt` next to this file; the Tor sources are BSD-3-Clause) | GPL-3.0, BSD-3-Clause | https://dist.torproject.org/tor-0.4.9.16.tar.gz, build recipe: https://gitlab.torproject.org/tpo/applications/tor-browser-build/-/tags/tbb-16.0.1-build1 |
| libevent 2.1 | BSD-3-Clause | https://libevent.org |
| OpenSSL 3 (Linux package only) | Apache-2.0 | https://www.openssl.org |
