#version 330

#moj_import <minecraft:globals.glsl>

// the view through the portal, rendered at the same resolution as the screen
uniform sampler2D Sampler0;

out vec4 fragColor;

void main() {
    vec2 uv = gl_FragCoord.xy / ScreenSize;
#ifdef FLIP_X
    // the view through a mirror is rendered as its mirror image
    uv.x = 1.0 - uv.x;
#endif
    fragColor = vec4(texture(Sampler0, uv).rgb, 1.0);
}
