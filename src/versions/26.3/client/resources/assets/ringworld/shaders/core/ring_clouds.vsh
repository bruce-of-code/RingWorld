#version 330
#extension GL_ARB_separate_shader_objects : require

// Distant cloud ring (experiment, 2026-11-22). Geometry and depth handling mirror
// ring_surface.vsh exactly (same global-cylinder vertices, same distortion branch, same
// far-depth clamp) so the layer sits in the same projection as the Atlas terrain.

#include <minecraft:dynamictransforms.glsl>
#include <minecraft:globals.glsl>
#include <ringworld:render_uniforms.glsl>
#include <ringworld:projection.glsl>
#include <minecraft:projection.glsl>

layout(location = 0) in vec3 Position;
layout(location = 1) in vec2 UV0;
layout(location = 2) in vec4 Color;

// Intrinsic (canonical X, Z) of the vertex, in blocks.
layout(location = 0) out vec2 intrinsicXZ;

const float TAU = 6.28318530717958647692;
const float FAR_BACKGROUND_DEPTH = 0.9999;

void main() {
    float vertexAngle = atan(Position.x, -Position.y);
    float originalRadius = length(Position.xy);
    vec3 projected = Position;
    if (RingWorldDistortion.x > 0.5) {
        vec3 point = vec3(vertexAngle * float(RingWorldLayout.y) / TAU,
                float(RingWorldLayout.y) / TAU + RingWorldVertical.x - originalRadius, Position.z);
        projected = ring_trial_position(point, TextureMat[3].xyz);
    }
    gl_Position = ProjMat * ModelViewMat * vec4(projected, 1.0);
    if (gl_Position.w > 0.0) {
#ifdef RINGWORLD_REVERSED_DEPTH
        gl_Position.z = max(gl_Position.z, gl_Position.w * ModelOffset.z);
#else
        gl_Position.z = min(gl_Position.z, gl_Position.w * FAR_BACKGROUND_DEPTH);
#endif
    }
    intrinsicXZ = UV0;
}
