# version 330 core

layout(std140) uniform;

uniform sampler2D s;

in vec2 imagePosition;

out vec4 outputColor;

struct PerLight
{
  vec4 cameraSpaceLightPos; // 0-16
  vec4 lightIntensity; // 16-32
  vec2 mapLightPos; // 32-40
  vec2 padding; // 40-48
};

const int maxNumberOfLights = 1000;

uniform Light
{
  vec4 ambientIntensity; // 0-16
  float lightAttenuation; // 16-20
  float maxIntensity; // 20-24
  float gamma; // 24-28
  int numberOfLights; // 28-32
  PerLight lights[maxNumberOfLights]; // 32-32+maxNumberOfLights*48
} Lgt;

float CalcAttenuation(in vec2 mapLightPos)
{
  vec2 lightDifference =  imagePosition - mapLightPos;
  float lightDistanceSqr = dot(lightDifference, lightDifference);
  lightDistanceSqr = lightDistanceSqr * lightDistanceSqr;
  return (1 / ( 1.0 + Lgt.lightAttenuation * lightDistanceSqr));
}

vec4 ComputeLighting(in vec4 texelColor, in PerLight lightData)
{
  float atten = CalcAttenuation(lightData.mapLightPos);
  vec4 lighting = texelColor * atten * lightData.lightIntensity;
  return lighting;
}

void main()
{
  vec4 texelColor = vec4(texelFetch(s, ivec2(imagePosition), 0).xyz, 1.0);
  vec4 accumLighting = vec4(0,0,0,0);
  
  for(int light = 0; light < Lgt.numberOfLights; light++)
  {
    accumLighting += ComputeLighting(texelColor, Lgt.lights[light]);
  }
  
  accumLighting = accumLighting / Lgt.maxIntensity;
  vec4 gamma = vec4(Lgt.gamma, Lgt.gamma, Lgt.gamma, 1.0);
  outputColor = pow(accumLighting, gamma);
}