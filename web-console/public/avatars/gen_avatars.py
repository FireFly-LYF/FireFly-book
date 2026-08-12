# -*- coding: utf-8 -*-
from pathlib import Path

OUT = Path(__file__).resolve().parent

AVATARS = [
    ("ff-01", "#FFE4E8", "#FFD5C8", "#5C4033", "#FF8FA3", "#FFB4C0", "#3D2B1F"),
    ("ff-02", "#E8F4FF", "#F6D7C5", "#2F3A4A", "#7EB6FF", "#F2B8A8", "#243447"),
    ("ff-03", "#E9F8F1", "#F3D0B8", "#6B4F3A", "#5ECF9A", "#F0B5A4", "#3A2A20"),
    ("ff-04", "#F3E8FF", "#FFD9CC", "#4A3B5C", "#C4A1FF", "#FFB7C5", "#352844"),
    ("ff-05", "#FFF4D6", "#F0C8A8", "#8B5A2B", "#FFC857", "#E8A090", "#4A3428"),
    ("ff-06", "#E6F7F9", "#EFC4AE", "#3E5560", "#5FD0DB", "#E9A898", "#2F4148"),
    ("ff-07", "#FFEFE0", "#F2C7B0", "#6D4C41", "#FF9F68", "#EFA392", "#3E2C26"),
    ("ff-08", "#FFE8F3", "#FFD0C2", "#5A3A4A", "#FF7AB2", "#FFB0C0", "#402832"),
    ("ff-09", "#EEF6E8", "#E8C4A8", "#4E5D3A", "#9ACD6A", "#E3A792", "#354028"),
    ("ff-10", "#EAEFFF", "#F1CDB8", "#334155", "#8BA4FF", "#E9A999", "#1E293B"),
    ("ff-11", "#FFF0E6", "#ECC0A4", "#7A4E2D", "#FF8A4C", "#E49A88", "#4A2F1C"),
    ("ff-12", "#F0F2F5", "#E6C2AD", "#475569", "#94A3B8", "#DFA392", "#334155"),
]


def svg(aid, bg, skin, hair, accent, cheek, eye):
    return f"""<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 128 128" width="128" height="128">
  <defs>
    <linearGradient id="g-{aid}" x1="0" y1="0" x2="1" y2="1">
      <stop offset="0%" stop-color="{bg}"/>
      <stop offset="100%" stop-color="{accent}" stop-opacity="0.55"/>
    </linearGradient>
  </defs>
  <circle cx="64" cy="64" r="64" fill="url(#g-{aid})"/>
  <ellipse cx="64" cy="58" rx="42" ry="40" fill="{hair}"/>
  <ellipse cx="64" cy="70" rx="30" ry="28" fill="{skin}"/>
  <path d="M34 58c8-18 52-18 60 0-10-10-20-14-30-14s-20 4-30 14z" fill="{hair}"/>
  <circle cx="46" cy="76" r="5.5" fill="{cheek}" opacity="0.75"/>
  <circle cx="82" cy="76" r="5.5" fill="{cheek}" opacity="0.75"/>
  <ellipse cx="52" cy="68" rx="3.2" ry="4" fill="{eye}"/>
  <ellipse cx="76" cy="68" rx="3.2" ry="4" fill="{eye}"/>
  <circle cx="53.2" cy="66.8" r="1.1" fill="#fff" opacity="0.9"/>
  <circle cx="77.2" cy="66.8" r="1.1" fill="#fff" opacity="0.9"/>
  <path d="M56 82c4 5 12 5 16 0" fill="none" stroke="{eye}" stroke-width="2.2" stroke-linecap="round"/>
  <circle cx="98" cy="30" r="6" fill="{accent}" opacity="0.85"/>
  <circle cx="28" cy="36" r="4" fill="#fff" opacity="0.55"/>
</svg>
"""


def main():
    for aid, bg, skin, hair, accent, cheek, eye in AVATARS:
        path = OUT / f"{aid}.svg"
        path.write_text(svg(aid, bg, skin, hair, accent, cheek, eye), encoding="utf-8")
        print("wrote", path.name)
    print("count", len(AVATARS))


if __name__ == "__main__":
    main()
