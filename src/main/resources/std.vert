# version 450 core

in vec2 pos;

uniform mat4 projectMatrix;
uniform float x;
uniform float y;
uniform mat2 rotateMatrix;
uniform float scale;
uniform vec3 color;

void main() {
  vec2 position = vec2(x,y)+scale*rotateMatrix*pos;
  gl_Position = projectMatrix * vec4(position, 0.0, 1.0);
}