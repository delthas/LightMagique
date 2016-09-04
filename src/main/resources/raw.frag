# version 330 core

const float gamma = 1/2.2;
uniform sampler2D s;

in vec2 imagePosition;

out vec4 outputColor;

void main()
{
  vec4 texelColor = texelFetch(s, ivec2(imagePosition), 0);
  outputColor = pow(texelColor, vec4(gamma,gamma,gamma,1.0));
}