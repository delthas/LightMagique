# version 450 core

layout(std140) uniform;

layout(location = 0) in vec2 position;

out vec2 imagePosition;

uniform mat4 texMatrix;

void main()
{
  gl_Position = texMatrix * vec4(position, 0.0, 1.0);
  imagePosition = position;
}