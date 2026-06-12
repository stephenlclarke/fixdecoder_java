# Icon Assets

`marvin.png` is the canonical source image for application icon assets in this repo.

- `marvin.ico` is retained for Windows builds of `fixdecoder.exe`.
- `marvin.icns` is kept for future macOS app-bundle packaging.
- `marvin.png` is kept for Linux desktop packaging or launcher metadata.

To regenerate the derived icon files:

```bash
make icons
```
