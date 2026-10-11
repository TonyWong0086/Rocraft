#version 330
#extension GL_ARB_separate_shader_objects : require

// core/entity.vsh with PER_FACE_LIGHTING, for a mesh kept on the GPU in its own part space: the part pose, colour,
// light and overlay come per draw from the RocraftMesh block instead of per vertex

#include <minecraft:light.glsl>
#include <minecraft:fog.glsl>
#include <minecraft:dynamictransforms.glsl>
#include <minecraft:projection.glsl>
#include <minecraft:sample_lightmap.glsl>

layout(std140) uniform RocraftMesh {
    mat4 MeshPose;
    mat4 MeshNormal;
    vec4 MeshColor;
    ivec4 MeshLightOverlay; // xy = UV2 (light), zw = UV1 (overlay)
};

layout(location = 0) in vec3 Position;
layout(location = 1) in vec2 UV0;
layout(location = 2) in vec3 Normal;

uniform sampler2D Sampler1;
uniform sampler2D Sampler2;

// locations as core/entity.vsh, so core/entity.fsh reads them
layout(location = 0) out float sphericalVertexDistance;
layout(location = 1) out float cylindricalVertexDistance;
layout(location = 2) out vec4 vertexPerFaceColorBack;
layout(location = 3) out vec4 vertexPerFaceColorFront;
layout(location = 4) out vec4 lightMapColor;
layout(location = 5) out vec4 overlayColor;
layout(location = 6) out vec2 texCoord0;

void main() {
    vec3 pos = (MeshPose * vec4(Position, 1.0)).xyz;
    vec3 normal = normalize(mat3(MeshNormal) * Normal);
    gl_Position = ProjMat * ModelViewMat * vec4(pos, 1.0);

    sphericalVertexDistance = fog_spherical_distance(pos);
    cylindricalVertexDistance = fog_cylindrical_distance(pos);

    vec2 light = minecraft_compute_light(Light0_Direction, Light1_Direction, normal);
    vertexPerFaceColorBack = minecraft_mix_light_separate(-light, MeshColor);
    vertexPerFaceColorFront = minecraft_mix_light_separate(light, MeshColor);

    lightMapColor = sample_lightmap(Sampler2, MeshLightOverlay.xy);
    overlayColor = texelFetch(Sampler1, MeshLightOverlay.zw, 0);
    texCoord0 = UV0;
}
