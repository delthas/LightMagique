# version 450 core

uniform sampler2D s;
uniform ivec2 offset;

out vec4 color;

void main() {
  color = texelFetch(s, ivec2(gl_FragCoord.xy) + offset, 0);
}