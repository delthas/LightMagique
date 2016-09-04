# version 330 core

layout(std140) uniform;

in vec3 vertexNormal;
in vec4 vertexColor;
in vec3 cameraSpacePosition;

out vec4 outputColor;

const float specularShininess = 0.01;

struct PerLight
{
  vec4 cameraSpaceLightPos; // 0-16
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


float CalcAttenuation(in vec3 cameraSpacePosition,
  in vec3 cameraSpaceLightPos,
  out vec3 lightDirection)
{
  vec3 lightDifference =  cameraSpaceLightPos - cameraSpacePosition;
  float lightDistanceSqr = dot(lightDifference, lightDifference);
  lightDirection = lightDifference * inversesqrt(lightDistanceSqr);
  
  return (1 / ( 1.0 + Lgt.lightAttenuation * lightDistanceSqr));
}

vec3 ComputeLighting(in PerLight lightData)
{
  vec3 lightDir;
  vec3 lightIntensity;
  if(lightData.cameraSpaceLightPos.w == 0.0)
  {
    lightDir = vec3(lightData.cameraSpaceLightPos);
    lightIntensity = lightData.lightIntensity;
  }
  else
  {
    float atten = CalcAttenuation(cameraSpacePosition,
      lightData.cameraSpaceLightPos.xyz, lightDir);
    lightIntensity = atten * lightData.lightIntensity;
  }
  
  vec3 surfaceNormal = normalize(vertexNormal);
  float cosAngIncidence = dot(surfaceNormal, lightDir);
  cosAngIncidence = clamp(cosAngIncidence, 0, 1);
  
  vec3 viewDirection = normalize(-cameraSpacePosition);
  
  vec3 halfAngle = normalize(lightDir + viewDirection);
  float blinnTerm = dot(surfaceNormal, halfAngle);
  blinnTerm = clamp(blinnTerm, 0, 1);
  blinnTerm = cosAngIncidence != 0.0 ? blinnTerm : 0.0;
  blinnTerm = pow(blinnTerm, specularShininess);
  
  vec3 lighting = vertexColor.rgb * lightIntensity * cosAngIncidence;
  lighting += vertexColor.rgb * lightIntensity * blinnTerm;
  
  return lighting;
}

void main()
{
  vec4 accumLighting = vertexColor * vec4(Lgt.ambientIntensity, 1.0);
  
  for(int light = 0; light < Lgt.numberOfLights; light++)
  {
    accumLighting.rgb += ComputeLighting(Lgt.lights[light]);
  }
  
  accumLighting.rgb = accumLighting.rgb / Lgt.maxIntensity;
  vec4 gamma = vec4(Lgt.gamma, Lgt.gamma, Lgt.gamma, 1.0);
  outputColor = pow(accumLighting, gamma);
}