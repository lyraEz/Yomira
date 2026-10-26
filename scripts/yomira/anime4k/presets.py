"""Official Anime4K v4 mpv presets (md/GLSL_Instructions_*.md of bloc97/Anime4K). Keep in step
with yomira.anime4k.Anime4KPresets in the app."""

_FAST = {"big": "M", "small": "S"}
_HQ = {"big": "VL", "small": "M"}


def _modes(size):
    big, small = size["big"], size["small"]
    pre = ["AutoDownscalePre_x2", "AutoDownscalePre_x4"]
    return {
        "A": ["Clamp_Highlights", f"Restore_CNN_{big}", f"Upscale_CNN_x2_{big}", *pre, f"Upscale_CNN_x2_{small}"],
        "B": ["Clamp_Highlights", f"Restore_CNN_Soft_{big}", f"Upscale_CNN_x2_{big}", *pre, f"Upscale_CNN_x2_{small}"],
        "C": ["Clamp_Highlights", f"Upscale_Denoise_CNN_x2_{big}", *pre, f"Upscale_CNN_x2_{small}"],
        "AA": ["Clamp_Highlights", f"Restore_CNN_{big}", f"Upscale_CNN_x2_{big}", f"Restore_CNN_{small}", *pre,
               f"Upscale_CNN_x2_{small}"],
        "BB": ["Clamp_Highlights", f"Restore_CNN_Soft_{big}", f"Upscale_CNN_x2_{big}", *pre,
               f"Restore_CNN_Soft_{small}", f"Upscale_CNN_x2_{small}"],
        "CA": ["Clamp_Highlights", f"Upscale_Denoise_CNN_x2_{big}", *pre, f"Restore_CNN_{small}",
               f"Upscale_CNN_x2_{small}"],
    }


PRESETS = {}
for _name, _size in (("FAST", _FAST), ("HQ", _HQ)):
    for _mode, _files in _modes(_size).items():
        PRESETS[f"{_mode}_{_name}"] = _files
