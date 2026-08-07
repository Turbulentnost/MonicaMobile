from pathlib import Path

from PIL import Image

SRC = Path(r"c:\Users\testii\Downloads\Monica\frontend\monica\public\logo512.png")
RES = Path(r"c:\Users\testii\Downloads\Monica\mobile\app\src\main\res")

LEGACY = {
    "mipmap-mdpi": 48,
    "mipmap-hdpi": 72,
    "mipmap-xhdpi": 96,
    "mipmap-xxhdpi": 144,
    "mipmap-xxxhdpi": 192,
}


def main() -> None:
    src = Image.open(SRC).convert("RGBA")
    print("source", src.size)

    # Legacy full icons: keep padding so round masks don't clip the bubble.
    legacy_scale = 0.78
    for folder, size in LEGACY.items():
        out_dir = RES / folder
        out_dir.mkdir(parents=True, exist_ok=True)
        canvas = Image.new("RGBA", (size, size), (0, 0, 0, 255))
        logo_side = max(1, int(size * legacy_scale))
        logo = src.resize((logo_side, logo_side), Image.Resampling.LANCZOS)
        ox = (size - logo_side) // 2
        oy = (size - logo_side) // 2
        canvas.paste(logo, (ox, oy), logo)
        canvas.save(out_dir / "ic_launcher.png", optimize=True)
        canvas.save(out_dir / "ic_launcher_round.png", optimize=True)
        print("wrote", folder, size, "logo_side", logo_side)

    # Adaptive foreground: 432x432 (xxxhdpi @108dp).
    # Safe zone for circle/squircle masks is ~66%; keep logo smaller so it fits.
    fg_size = 432
    safe_scale = 0.56
    canvas = Image.new("RGBA", (fg_size, fg_size), (0, 0, 0, 255))
    logo_side = int(fg_size * safe_scale)
    logo = src.resize((logo_side, logo_side), Image.Resampling.LANCZOS)
    ox = (fg_size - logo_side) // 2
    oy = (fg_size - logo_side) // 2
    canvas.paste(logo, (ox, oy), logo)
    fg_path = RES / "drawable" / "ic_launcher_foreground.png"
    canvas.save(fg_path, optimize=True)
    print("wrote foreground", canvas.size, "logo_side", logo_side)
    print("done")


if __name__ == "__main__":
    main()
