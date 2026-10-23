# Third-party assets

Assets bundled in the app that are not covered by a Gradle dependency's own license metadata.

| Asset | Version | Where it is used | Source | License |
|---|---|---|---|---|
| Schibsted Grotesk (static TTF, weights 400 Regular, 500 Medium, 600 SemiBold, 700 Bold, 800 ExtraBold) | 1.100, upstream commit `d485f61f105e1b3935f4d21dfb4d371359798603` (2023-03-03) | `core/designsystem/src/main/res/font/schibsted_grotesk_*.ttf` (PRD §11.1, DEV-004) | https://github.com/schibsted/schibsted-grotesk (`fonts/ttf/SchibstedGrotesk-*.ttf`) | SIL Open Font License 1.1, see [`SchibstedGrotesk-OFL.txt`](SchibstedGrotesk-OFL.txt) |

The font files are unmodified copies, renamed to lowercase to be valid Android resource names. The OFL allows
bundling them with the app as long as the license travels with them and the fonts are not sold on their own.
