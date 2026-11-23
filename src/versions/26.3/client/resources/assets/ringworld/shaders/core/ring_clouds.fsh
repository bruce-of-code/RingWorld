#version 330
#extension GL_ARB_separate_shader_objects : require

// Distant cloud ring (experiment, 2026-11-22): one flat sheet at the cloud deck, cut from
// vanilla's clouds.png with vanilla's cell size, drift and Z offset, so it continues the
// live 3-D clouds beyond their range. Inputs (see RingCloudRing):
//   ColorModulator      vanilla cloud colour and alpha for this frame
//   ModelOffset.x / .y  camera canonical angle / Z
//   TextureMat[0].xy    fade-in start / end distance (vanilla cloud range)
//   TextureMat[1].xy    pattern offset X (time drift) / Z (vanilla's 3.96)
//   TextureMat[2].xyz   orbital day: world day fraction / active flag / cos(tilt)

#include <minecraft:dynamictransforms.glsl>
#include <minecraft:globals.glsl>
#include <ringworld:render_uniforms.glsl>

uniform sampler2D Sampler0;

layout(location = 0) in vec2 intrinsicXZ;

layout(location = 0) out vec4 fragColor;

const float TAU = 6.28318530717958647692;


// Orbital day (experiment 2026-11-22). TextureMat[2]: x = world day fraction, y = 1 when the
// orbital cycle is active, z = cos(tilt). Local day fraction at a point is the world fraction
// minus its longitude's share (RingOrbitalDay), so the sun's local elevation sine is
// cos(tilt) * cos(TAU * (local - 0.25)). Returns 0 (night) .. 1 (full day).
float ring_orbital_daylight(float canonicalAngle, out float elevation) {
    float local = TextureMat[2].x - canonicalAngle / 6.28318530717958647692;
    elevation = TextureMat[2].z * cos(6.28318530717958647692 * (local - 0.25));
    return smoothstep(-0.10, 0.25, elevation);
}

vec3 ring_orbital_light(float daylight, float elevation) {
    vec3 night = vec3(0.045, 0.050, 0.085);
    // Warm only in the last ~6 degrees: the ring's thin air reddens light just at the horizon.
    vec3 day = mix(vec3(1.00, 0.66, 0.40), vec3(1.0), smoothstep(0.0, 0.10, elevation));
    return mix(night, day, daylight);
}

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
    vec3 cloud = ColorModulator.rgb;
    if (TextureMat[2].y > 0.5) {
        // Each distant cloud is lit by its own local sun, not the camera's cloud colour.
        float elevation;
        float daylight = ring_orbital_daylight(TAU * intrinsicXZ.x / circumference, elevation);
        cloud = ring_orbital_light(daylight, elevation);
    }
    fragColor = vec4(cloud * 0.82, alpha);
}
