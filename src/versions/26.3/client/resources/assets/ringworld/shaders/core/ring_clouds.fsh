#version 330
#extension GL_ARB_separate_shader_objects : require

// Distant cloud ring (experiment, 2026-11-22): one flat sheet at the cloud deck, cut from
// vanilla's clouds.png with vanilla's cell size, drift and Z offset, so it continues the
// live 3-D clouds beyond their range. Inputs (see RingCloudRing):
//   ColorModulator      vanilla cloud colour and alpha for this frame
//   ModelOffset.x / .y  camera canonical angle / Z
//   TextureMat[0].xy    fade-in start / end distance (vanilla cloud range)
//   TextureMat[1].xy    pattern offset X (time drift) / Z (vanilla's 3.96)

#include <minecraft:dynamictransforms.glsl>
#include <minecraft:globals.glsl>
#include <ringworld:render_uniforms.glsl>

uniform sampler2D Sampler0;

layout(location = 0) in vec2 intrinsicXZ;

layout(location = 0) out vec4 fragColor;

const float TAU = 6.28318530717958647692;

void main() {
    float circumference = float(RingWorldLayout.y);
    float cameraX = ModelOffset.x * circumference / TAU;
    float dx = mod(intrinsicXZ.x - cameraX + 0.5 * circumference, circumference) - 0.5 * circumference;
    float distance = length(vec2(dx, intrinsicXZ.y - ModelOffset.y));
    // Hand over from the live clouds at the edge of their range.
    float fadeIn = smoothstep(TextureMat[0].x, TextureMat[0].y, distance);
    if (fadeIn <= 0.001) discard;

    ivec2 size = textureSize(Sampler0, 0);
    vec2 cell = floor((intrinsicXZ + TextureMat[1].xy) / 12.0);
    vec4 texel = texelFetch(Sampler0, ivec2(mod(cell, vec2(size))), 0);
    if (texel.a < 0.04) discard;

    // Thin toward the far side so the band reads as haze, not a painted ceiling.
    float fraction = clamp(distance / (0.5 * circumference), 0.0, 1.0);
    float alpha = ColorModulator.a * fadeIn * mix(1.0, 0.45, pow(fraction, 0.7));
    // Vanilla shades a cloud underside 0.7 and top 1.0; a flat sheet seen from either
    // side splits the difference.
    fragColor = vec4(ColorModulator.rgb * 0.82, alpha);
}
