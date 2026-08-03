#version 150

uniform sampler2D DiffuseSampler;

uniform float Time;
uniform vec2  OutSize;

in vec2 texCoord;

out vec4 fragColor;

/* Old-camcorder hash: everything is driven by integer tape-frame steps so
   the noise jumps instead of sliding smoothly. */
float hash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453123);
}

void main() {
    // The post processor advances Time by the frame delta each pass; a whole
    // 0..1 loop covers ~20 real seconds. Rescale to "tape frames": roll steps
    // by 1 every rendered frame at 60 FPS, so the noise has a fresh seed each
    // frame and the tracking band wanders down the screen about once a second.
    float roll = Time * 1200.0;
    float tapeFrame = floor(roll);
    float drift = fract(roll);

    vec2 centered = texCoord * 2.0 - 1.0;
    float dist = length(centered);

    // ---- 4D glasses: horizontal red/cyan anaglyph shift -----------------------
    // Constant horizontal shift driven by the discrete tape frame only — no
    // radial/per-frame drift, so the camera no longer jitters as you look
    // around, and the red/cyan fringes are stable instead of wobbling.
    float slip = 0.006
        + 0.0025 * sin(tapeFrame * 0.194)
        + 0.0018 * sin(tapeFrame * 0.421);
    vec2 shift = vec2(slip, 0.0);

    vec4 red  = texture(DiffuseSampler, texCoord + shift);
    vec4 cyan = texture(DiffuseSampler, texCoord - shift);

    vec3 col;
    col.r = red.r;
    col.g = cyan.g;
    col.b = cyan.b;

    // ---- rolling tracking band (a wandering tear in the tape) -------------
    float bandY = drift;
    float bandMask = exp(-(texCoord.y - bandY) * (texCoord.y - bandY) * 380.0);
    float tear = 0.5 - hash(vec2(floor(texCoord.y * 380.0) + tapeFrame * 1.7, tapeFrame));
    vec2 tornUV = texCoord + vec2(tear * bandMask * 0.35, 0.0);
    vec3 torn = texture(DiffuseSampler, tornUV).rgb;
    col = mix(col, torn, bandMask * 0.7);

    // ---- interlaced scanlines ---------------------------------------------
    float scan = 0.82 + 0.18 * pow(sin(texCoord.y * OutSize.y * 3.14159265), 2.0);
    col *= scan;

    // ---- crawling static + occasional dropout -------------------------------
    float grain = hash(gl_FragCoord.xy + vec2(tapeFrame * 7.0, tapeFrame * 3.0));
    col += (grain - 0.5) * 0.16;
    col *= 1.0 - step(0.86, hash(vec2(tapeFrame, 13.0))) * 0.12;

    // ---- old-monitor color: fade toward grey, high-contrast ----------------
    float luminance = dot(col, vec3(0.2126, 0.7152, 0.0722));
    col = mix(vec3(luminance), col, 0.62);
    col = clamp(col, 0.0, 1.0);

    // ---- vignette + brightness flicker --------------------------------------
    col *= mix(0.42, 1.0, smoothstep(1.42, 0.25, dist));
    col *= 0.93 + 0.07 * hash(vec2(tapeFrame, 7.0));

    fragColor = vec4(col, 1.0);
}