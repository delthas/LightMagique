# version 330 core

layout(std140) uniform;

uniform sampler2D s;

in vec2 imagePosition;

out vec4 outputColor;

struct PerLight
{
  vec3 cameraSpaceLightPos; // 0-16
  vec3 lightIntensity; // 16-32
  vec2 mapLightPos; // 32-40
  vec2 padding; // 40-48
};

const int maxNumberOfLights = 1000;

uniform Light
{
  vec3 ambientIntensity; // 0-12
  float lightAttenuation; // 12-16
  float maxIntensity; // 16-20
  float gamma; // 20-24
  int numberOfLights; // 24-28
  float padding; // 28-32
  vec3 fogIntensity; // 32-44
  float fogAttenuation; // 44-48
  PerLight lights[maxNumberOfLights]; // 48-48+maxNumberOfLights*48  
} Lgt;

void main()
{
  vec3 texelColor = texelFetch(s, ivec2(imagePosition), 0).xyz;
  
  vec3 intensity = vec3(0.0,0.0,0.0);
  
  for(int light = 0; light < Lgt.numberOfLights; light++)
  {
    PerLight lightData = Lgt.lights[light];
    vec2 lightDifference =  imagePosition - lightData.mapLightPos;
    float lightDistanceSqr = dot(lightDifference, lightDifference);
    float lightDistance = sqrt(lightDistanceSqr);
    float atten = (1 / ( 1.0 + Lgt.lightAttenuation * lightDistance));
    intensity += atten * lightData.lightIntensity * exp(-lightDistance/Lgt.fogAttenuation * (vec3(1.0,1.0,1.0)-Lgt.fogIntensity));
  }
  
  intensity = intensity / Lgt.maxIntensity;
  
  outputColor = vec4(texelColor * pow(intensity, vec3(Lgt.gamma,Lgt.gamma,Lgt.gamma)), 1.0);
}