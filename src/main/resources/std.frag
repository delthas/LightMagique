# version 450 core

layout(std140) uniform;

in vec3 vertexNormal;
in vec4 vertexColor;
in vec3 cameraSpacePosition;

out vec4 outputColor;

const float specularShininess = 0.01;

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


float CalcAttenuation(in vec3 cameraSpacePosition,
  in vec3 cameraSpaceLightPos,
  out vec3 lightDirection)
{
  vec3 lightDifference =  cameraSpaceLightPos - cameraSpacePosition;
  float lightDistanceSqr = dot(lightDifference, lightDifference);
  lightDirection = lightDifference * inversesqrt(lightDistanceSqr);
  
  return (1 / ( 1.0 + Lgt.lightAttenuation * lightDistanceSqr));
}

vec4 ComputeLighting(in PerLight lightData)
{
  vec3 lightDir;
  vec4 lightIntensity;
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
  cosAngIncidence = cosAngIncidence < 0.0001 ? 0.0 : cosAngIncidence;
  
  vec3 viewDirection = normalize(-cameraSpacePosition);
  
  vec3 halfAngle = normalize(lightDir + viewDirection);
  float angleNormalHalf = acos(dot(halfAngle, surfaceNormal));
  float exponent = angleNormalHalf / specularShininess;
  exponent = -(exponent * exponent);
  float gaussianTerm = exp(exponent);

  gaussianTerm = cosAngIncidence != 0.0 ? gaussianTerm : 0.0;
  
  vec4 lighting = vertexColor * lightIntensity * cosAngIncidence;
  lighting += vertexColor * lightIntensity * gaussianTerm;
  
  return lighting;
}

void main()
{
  vec4 accumLighting = vertexColor * Lgt.ambientIntensity;
  for(int light = 0; light < Lgt.numberOfLights; light++)
  {
    accumLighting += ComputeLighting(Lgt.lights[light]);
  }
  
  accumLighting = accumLighting / Lgt.maxIntensity;
  vec4 gamma = vec4(Lgt.gamma, Lgt.gamma, Lgt.gamma, 1.0);
  outputColor = pow(accumLighting, gamma);
}