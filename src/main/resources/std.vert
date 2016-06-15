# version 330 core

layout(std140) uniform;

layout(location = 0) in vec3 position;
layout(location = 1) in vec3 normal;
layout(location = 2) in vec3 color;

out vec3 vertexNormal;
out vec4 vertexColor;
out vec3 cameraSpacePosition;

uniform mat4 p;
uniform mat4 vm;
uniform mat3 vmNormal;

void main()
{
  vec4 tempCamPosition = vm * vec4(position, 1.0);
  gl_Position = p * tempCamPosition;

  vertexNormal = normalize(vmNormal * normal);
  vertexColor = vec4(color,1.0);
  cameraSpacePosition = vec3(tempCamPosition);
}