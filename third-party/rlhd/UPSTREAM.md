# 117 HD source snapshot

Source: https://github.com/117HD/RLHD

Version: 1.6.0

Commit: f5be256078b5e398089aa73e48116c7f95dc329f

Selected from the official RuneLite Plugin Hub `plugins/117hd` manifest on
2026-10-19. The source and resources retain their upstream BSD license.

This snapshot carries the existing Android host, GLES shader, texture upload,
framebuffer and memory adaptations. The 1.6 integration adds GLES casts and
sampler precision for the new sky, water and shadow shaders, Android bridge
entry points, and capability-based face-buffer selection. See
`android-hd/README.md` for validation and remaining runtime limits.
