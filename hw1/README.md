## HW1 PCM and Compounding
![demo](CSCI576_hw1_demo.gif)

# Compile
`javac ImageDisplay.java`

# Run
`java ImageDisplay <pathToRGBVideo> <scale> <quantization> <mode>`
- `scale`: 0 < floating-point number <= 1; controls how much to downsample pixels.
- `quantization`: 1 <= integer <= 8; controls how much to quantize each color channel; 1 means maximum quantization and loss.
- `mode`: -1 <= integer <= 255; -1 means uniform quantization; 0~255 means logarithmic quantization with `mode` value being the pivot.
