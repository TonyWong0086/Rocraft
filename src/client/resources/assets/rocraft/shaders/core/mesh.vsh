#version 330

// core/entity.vsh with PER_FACE_LIGHTING, for a mesh kept on the GPU in its own part space: the part pose, colour,
// light and overlay come per draw from the RocraftMesh block instead of per vertex

#moj_import <minecraft:light.glsl>
#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>
#moj_import <minecraft:sample_lightmap.glsl>

layout(std140) uniform RocraftMesh {
    mat4 MeshPose;
    mat4 MeshNormal;
    vec4 MeshColor;
    ivec4 MeshLightOverlay; // xy = UV2 (light), zw = UV1 (overlay)
};

in vec3 Position;
in vec2 UV0;
in vec3 Normal;

uniform sampler2D Sampler1;
uniform sampler2D Sampler2;

out float sphericalVertexDistance;
out float cylindricalVertexDistance;
out vec4 vertexPerFaceColorBack;
out vec4 vertexPerFaceColorFront;
out vec4 lightMapColor;
out vec4 overlayColor;
out vec2 texCoord0;

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
