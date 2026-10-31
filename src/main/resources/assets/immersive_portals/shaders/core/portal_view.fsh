#version 330

#moj_import <minecraft:globals.glsl>

// the view through the portal, rendered at the same resolution as the screen
uniform sampler2D Sampler0;

out vec4 fragColor;

void main() {
    fragColor = vec4(texture(Sampler0, gl_FragCoord.xy / ScreenSize).rgb, 1.0);
}
