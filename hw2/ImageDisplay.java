import java.awt.*;
import java.awt.image.*;
import java.io.*;
import static java.lang.Thread.sleep;
import javax.swing.*;


public class ImageDisplay {

    JFrame frame;
    final int width = 352; // default image width and height
    final int height = 288;
    final int compression_block_size = 8;
    BufferedImage imgOne = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
    JLabel lbIm1;
    BufferedImage imgTwo = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
    JLabel lbIm2;

    class IntImage {

        int w;
        int h;
        int[][][] data;

        IntImage(String imgPath, int width, int height) throws FileNotFoundException, IOException {
            File file = new File(imgPath);
            RandomAccessFile raf = new RandomAccessFile(file, "r");
            raf.seek(0);
            byte[] bytes = new byte[width*height*3];
            raf.read(bytes);

            w = width;
            h = height;
            data = new int[height][width][3];
            for (int i=0; i<height*width; i++) {
                data[i/width][i%width][0] = bytes[i] & 0xff;
                data[i/width][i%width][1] = bytes[i+height*width] & 0xff;
                data[i/width][i%width][2] = bytes[i+height*width*2] & 0xff;
            }
        }

        IntImage(byte[] byte_image, int width, int height) {
            w = width;
            h = height;
            data = new int[height][width][3];
            for (int i=0; i<height*width; i++) {
                data[i/width][i%width][0] = byte_image[i] & 0xff;
                data[i/width][i%width][1] = byte_image[i+height*width] & 0xff;
                data[i/width][i%width][2] = byte_image[i+height*width*2] & 0xff;
            }
        }

        IntImage(int[][][] int_image) {
            w = int_image[0].length;
            h = int_image.length;
            data = new int[h][w][3];
            for (int r=0; r<h; r++) {
                for (int c=0; c<w; c++) {
                    data[r][c][0] = int_image[r][c][0];
                    data[r][c][1] = int_image[r][c][1];
                    data[r][c][2] = int_image[r][c][2];
                }
            }
        }

        byte[] ByteImage() {
            byte[] result = new byte[w*h*3];
            for (int r=0; r<h; r++) {
                for (int c=0; c<w; c++) {
                    int clamped_r = Math.max(0, Math.min(255, data[r][c][0]));
                    int clamped_g = Math.max(0, Math.min(255, data[r][c][1]));
                    int clamped_b = Math.max(0, Math.min(255, data[r][c][2]));
                    result[r*w+c] = (byte) clamped_r;
                    result[r*w+c+w*h] = (byte) clamped_g;
                    result[r*w+c+w*h*2] = (byte) clamped_b;
                }
            }

            return result;
        }

        void BufferedImageSetRGB(BufferedImage img) {
            byte[] display = this.ByteImage();

            int ind = 0;
            for(int y = 0; y < h; y++)
            {
                for(int x = 0; x < w; x++)
                {
                    byte r = display[ind];
                    byte g = display[ind+h*w];
                    byte b = display[ind+h*w*2];

                    int pix = 0xff000000 | ((r & 0xff) << 16) | ((g & 0xff) << 8) | (b & 0xff);

                    img.setRGB(x,y,pix);
                    ind++;
                }
            }
        }
    }

    class Encoder {

        int quantization_level;
        double q_scale;
        double c0;
        int block_size = 8;
        double[][] cosine_table;

        Encoder(int quantization_level, int block_size) {
            this.quantization_level = quantization_level;
            q_scale = 4 * Math.pow(2, quantization_level);
            c0 = 1/Math.sqrt(2);
            this.block_size = block_size;
            cosine_table = new double[block_size][block_size];

            for (int xy=0; xy<block_size; xy++) {
                for (int uv=0; uv<block_size; uv++) {
                    cosine_table[xy][uv] = Math.cos((2*xy+1)*uv*Math.PI / 16);
                }
            }
        }

        void encode(int[][][] block, int[][][] DCTQ, int upto_AC) {
            assert block.length == block_size && block[0].length == block_size 
                    && block[0][0].length == 3;
            assert DCTQ.length == block_size && DCTQ[0].length == block_size 
                    && DCTQ[0][0].length == 3;
            assert 0 <= quantization_level && quantization_level <= 7;
            assert 0 <= upto_AC && upto_AC < block_size*block_size;

            int AC_counter = 0;
            double cu, cv;
            double sum_r, sum_g, sum_b;
            for (int u=0; u<block_size; u++) {
                for (int v=0; v<block_size; v++) {
                    if (AC_counter > upto_AC) {
                        DCTQ[u][v][0] = 0;
                        DCTQ[u][v][1] = 0;
                        DCTQ[u][v][2] = 0;
                        continue;
                    }

                    cu = (u==0) ? c0 : 1;
                    cv = (v==0) ? c0 : 1;
                    sum_r = 0; sum_g = 0; sum_b = 0;
                    for (int x=0; x<block_size; x++) {
                        for (int y=0; y<block_size; y++) {
                            sum_r += block[x][y][0] * cosine_table[x][u] * cosine_table[y][v];
                            sum_g += block[x][y][1] * cosine_table[x][u] * cosine_table[y][v];
                            sum_b += block[x][y][2] * cosine_table[x][u] * cosine_table[y][v];
                        }
                    }
                    DCTQ[u][v][0] = (int) Math.round(sum_r * cu * cv / q_scale);
                    DCTQ[u][v][1] = (int) Math.round(sum_g * cu * cv / q_scale);
                    DCTQ[u][v][2] = (int) Math.round(sum_b * cu * cv / q_scale);

                    AC_counter++;
                }
            }
        }

        int[][][] full_encode(int[][][] source) {
            assert source[0][0].length == 3;
            assert 0 <= quantization_level && quantization_level <= 7;
            int nrows = source.length, ncols = source[0].length;

            int DCTQ_nrows = (int) Math.ceil(nrows/block_size)*block_size;
            int DCTQ_ncols = (int) Math.ceil(ncols/block_size)*block_size;
            int[][][] DCTQ = new int[DCTQ_nrows][DCTQ_ncols][3];

            int padded_br, padded_bc;
            int[][][] DCTQ_placeholder = new int[block_size][block_size][3];
            int[][][] block_placeholder = new int[block_size][block_size][3];
            for (int r=0; r<(nrows/block_size); r++) {
                for (int c=0; c<(ncols/block_size); c++) {

                    for (int br=0; br<block_size; br++) {
                        for (int bc=0; bc<block_size; bc++) {
                            padded_br = r*block_size+br;
                            padded_bc = c*block_size+bc;
                            if (padded_br < nrows && padded_bc < ncols) {
                                block_placeholder[br][bc][0] = source[padded_br][padded_bc][0];
                                block_placeholder[br][bc][1] = source[padded_br][padded_bc][1];
                                block_placeholder[br][bc][2] = source[padded_br][padded_bc][2];
                            } else {
                                block_placeholder[br][bc][0] = 0;
                                block_placeholder[br][bc][1] = 0;
                                block_placeholder[br][bc][2] = 0;
                            }
                        }
                    }

                    encode(block_placeholder, DCTQ_placeholder, block_size*block_size-1);

                    for (int br=0; br<block_size; br++) {
                        for (int bc=0; bc<block_size; bc++) {
                            padded_br = r*block_size+br;
                            padded_bc = c*block_size+bc;
                            DCTQ[padded_br][padded_bc][0] = DCTQ_placeholder[br][bc][0];
                            DCTQ[padded_br][padded_bc][1] = DCTQ_placeholder[br][bc][1];
                            DCTQ[padded_br][padded_bc][2] = DCTQ_placeholder[br][bc][2];
                        }
                    }
                }
            }

            return DCTQ;
        }
    }

    class Decoder {

        int quantization_level;
        double q_scale;
        double c0;
        int block_size = 8;
        double[][] cosine_table;

        Decoder(int quantization_level, int block_size) {
            this.quantization_level = quantization_level;
            q_scale = Math.pow(2, quantization_level);
            c0 = 1/Math.sqrt(2);
            this.block_size = block_size;
            cosine_table = new double[block_size][block_size];

            for (int xy=0; xy<block_size; xy++) {
                for (int uv=0; uv<block_size; uv++) {
                    cosine_table[xy][uv] = Math.cos((2*xy+1)*uv*Math.PI / 16);
                }
            }
        }

        void decode(int[][][] DCTQ, int[][][] block, int upto_AC) {
            assert block.length == block_size && block[0].length == block_size 
                    && block[0][0].length == 3;
            assert DCTQ.length == block_size && DCTQ[0].length == block_size 
                    && DCTQ[0][0].length == 3;
            assert 0 <= quantization_level && quantization_level <= 7;
            assert 0 <= upto_AC && upto_AC < block_size*block_size;

            int AC_counter;
            double sum_r, sum_g, sum_b;
            double cu, cv;
            for (int x=0; x<block_size; x++) {
                for (int y=0; y<block_size; y++) {
                    AC_counter = 0;
                    sum_r = 0; sum_g = 0; sum_b = 0;

                    outerloop:
                    for (int u=0; u<block_size; u++) {
                        for (int v=0; v<block_size; v++) {
                            if (AC_counter > upto_AC) {
                                break outerloop;
                            }

                            cu = (u==0) ? c0 : 1;
                            cv = (v==0) ? c0 : 1;
                            sum_r += cu * cv * DCTQ[u][v][0] * q_scale * 
                                    cosine_table[x][u] * cosine_table[y][v];
                            sum_g += cu * cv * DCTQ[u][v][1] * q_scale * 
                                    cosine_table[x][u] * cosine_table[y][v];
                            sum_b += cu * cv * DCTQ[u][v][2] * q_scale * 
                                    cosine_table[x][u] * cosine_table[y][v];

                            AC_counter++;
                        }
                    }

                    block[x][y][0] = (int) Math.round(sum_r/4);
                    block[x][y][1] = (int) Math.round(sum_g/4);
                    block[x][y][2] = (int) Math.round(sum_b/4);
                }
            }
        }
    }

    int highest_num_bit_two_complement(int number) {
        if (number > 0) {
            return (int) Math.ceil(Math.log(number+1) / Math.log(2))-1;
        } else if (number < 0) {
            return (int) Math.ceil(Math.log(-number) / Math.log(2));
        } else {
            return 0;
        }
    }

    void update_lbIm2(IntImage source, int quantization_level, int delivery_mode, int latency) throws Exception {
        int[][][] image = source.data;
        int nrows = source.h;
        int ncols = source.w;
        assert nrows == height && ncols == width;
        Encoder e = new Encoder(quantization_level, compression_block_size);
        Decoder d = new Decoder(quantization_level, compression_block_size);

        int[][][] DCTQ = e.full_encode(image);
        int DCTQ_nrows = DCTQ.length;
        int DCTQ_ncols = DCTQ[0].length;

        IntImage restored = new IntImage(new int[height][width][3]);

        int[][][] DCTQ_placeholder, block_placeholder;
        int padded_br, padded_bc;
        switch(delivery_mode) {
            case 1:
                DCTQ_placeholder = new int[compression_block_size][compression_block_size][3];
                block_placeholder = new int[compression_block_size][compression_block_size][3];
                for (int r=0; r<(nrows/compression_block_size); r++) {
                    for (int c=0; c<(ncols/compression_block_size); c++) {

                        for (int br=0; br<compression_block_size; br++) {
                            for (int bc=0; bc<compression_block_size; bc++) {
                                padded_br = r*compression_block_size+br;
                                padded_bc = c*compression_block_size+bc;
                                DCTQ_placeholder[br][bc][0] = DCTQ[padded_br][padded_bc][0];
                                DCTQ_placeholder[br][bc][1] = DCTQ[padded_br][padded_bc][1];
                                DCTQ_placeholder[br][bc][2] = DCTQ[padded_br][padded_bc][2];
                            }
                        }

                        d.decode(DCTQ_placeholder, block_placeholder, 
                                compression_block_size*compression_block_size-1);

                        for (int br=0; br<compression_block_size; br++) {
                            for (int bc=0; bc<compression_block_size; bc++) {
                                padded_br = r*compression_block_size+br;
                                padded_bc = c*compression_block_size+bc;
                                if (padded_br < nrows && padded_bc < ncols) {
                                    restored.data[padded_br][padded_bc][0] =
                                            block_placeholder[br][bc][0];
                                    restored.data[padded_br][padded_bc][1] =
                                            block_placeholder[br][bc][1];
                                    restored.data[padded_br][padded_bc][2] =
                                            block_placeholder[br][bc][2];
                                }
                            }
                        }

                        restored.BufferedImageSetRGB(imgTwo);
                        frame.repaint();
                        sleep(latency);
                    }
                }
                break;

            case 2:
                DCTQ_placeholder = new int[compression_block_size][compression_block_size][3];
                block_placeholder = new int[compression_block_size][compression_block_size][3];
                for (int i=0; i<compression_block_size*compression_block_size; i++) {
                    for (int r=0; r<(nrows/compression_block_size); r++) {
                        for (int c=0; c<(ncols/compression_block_size); c++) {

                            for (int br=0; br<compression_block_size; br++) {
                                for (int bc=0; bc<compression_block_size; bc++) {
                                    padded_br = r*compression_block_size+br;
                                    padded_bc = c*compression_block_size+bc;
                                    DCTQ_placeholder[br][bc][0] = DCTQ[padded_br][padded_bc][0];
                                    DCTQ_placeholder[br][bc][1] = DCTQ[padded_br][padded_bc][1];
                                    DCTQ_placeholder[br][bc][2] = DCTQ[padded_br][padded_bc][2];
                                }
                            }

                            d.decode(DCTQ_placeholder, block_placeholder, i);

                            for (int br=0; br<compression_block_size; br++) {
                                for (int bc=0; bc<compression_block_size; bc++) {
                                    padded_br = r*compression_block_size+br;
                                    padded_bc = c*compression_block_size+bc;
                                    if (padded_br < nrows && padded_bc < ncols) {
                                        restored.data[padded_br][padded_bc][0] =
                                                block_placeholder[br][bc][0];
                                        restored.data[padded_br][padded_bc][1] =
                                                block_placeholder[br][bc][1];
                                        restored.data[padded_br][padded_bc][2] =
                                                block_placeholder[br][bc][2];
                                    }
                                }
                            }
                        }
                    }

                    restored.BufferedImageSetRGB(imgTwo);
                    frame.repaint();
                    sleep(latency);
                }
                break;

            case 3:
                int[][][] shift_by = new int[DCTQ_nrows][DCTQ_ncols][3];
                for (int r=0; r<DCTQ_nrows; r++) {
                    for (int c=0; c<DCTQ_ncols; c++) {
                        for (int chan=0; chan<3; chan++) {
                            shift_by[r][c][chan] = highest_num_bit_two_complement(DCTQ[r][c][chan]);
                        }
                    }
                }

                DCTQ_placeholder = new int[compression_block_size][compression_block_size][3];
                block_placeholder = new int[compression_block_size][compression_block_size][3];
                int chan1_shift, chan2_shift, chan3_shift;
                while (true) {
                    boolean seen_non_zero_shift = false;
                    for (int r=0; r<(nrows/compression_block_size); r++) {
                        for (int c=0; c<(ncols/compression_block_size); c++) {

                            for (int br=0; br<compression_block_size; br++) {
                                for (int bc=0; bc<compression_block_size; bc++) {
                                    padded_br = r*compression_block_size+br;
                                    padded_bc = c*compression_block_size+bc;

                                    chan1_shift = shift_by[padded_br][padded_bc][0];
                                    chan2_shift = shift_by[padded_br][padded_bc][1];
                                    chan3_shift = shift_by[padded_br][padded_bc][2];
                                    if (chan1_shift != 0 || chan2_shift != 0 || chan3_shift != 0) {
                                        seen_non_zero_shift = true;
                                    }

                                    DCTQ_placeholder[br][bc][0] = DCTQ[padded_br][padded_bc][0]
                                            >> chan1_shift << chan1_shift;
                                    shift_by[padded_br][padded_bc][0] = 
                                            Math.max(shift_by[padded_br][padded_bc][0]-1, 0);

                                    DCTQ_placeholder[br][bc][1] = DCTQ[padded_br][padded_bc][1]
                                            >> chan2_shift << chan2_shift;
                                    shift_by[padded_br][padded_bc][1] = 
                                            Math.max(shift_by[padded_br][padded_bc][1]-1, 0);

                                    DCTQ_placeholder[br][bc][2] = DCTQ[padded_br][padded_bc][2]
                                            >> chan3_shift << chan3_shift;
                                    shift_by[padded_br][padded_bc][2] = 
                                            Math.max(shift_by[padded_br][padded_bc][2]-1, 0);
                                }
                            }

                            d.decode(DCTQ_placeholder, block_placeholder, 
                                    compression_block_size*compression_block_size-1);

                            for (int br=0; br<compression_block_size; br++) {
                                for (int bc=0; bc<compression_block_size; bc++) {
                                    padded_br = r*compression_block_size+br;
                                    padded_bc = c*compression_block_size+bc;
                                    if (padded_br < nrows && padded_bc < ncols) {
                                        restored.data[padded_br][padded_bc][0] =
                                                block_placeholder[br][bc][0];
                                        restored.data[padded_br][padded_bc][1] =
                                                block_placeholder[br][bc][1];
                                        restored.data[padded_br][padded_bc][2] =
                                                block_placeholder[br][bc][2];
                                    }
                                }
                            }
                        }
                    }
                    restored.BufferedImageSetRGB(imgTwo);
                    frame.repaint();
                    sleep(latency);
                    if (!seen_non_zero_shift) {
                        break;
                    }
                }
                break;
            default:
                throw new Exception("unknown delivery mode");
        }
    }

    public void showIms(String[] args) throws FileNotFoundException, IOException, Exception{
        IntImage source = new IntImage(args[0], width, height);
        source.BufferedImageSetRGB(imgOne);

        int q = Integer.parseInt(args[1]);
        int l = Integer.parseInt(args[2]);
        int d = Integer.parseInt(args[3]);

        frame = new JFrame();
        GridBagLayout gLayout = new GridBagLayout();
        frame.getContentPane().setLayout(gLayout);

        lbIm1 = new JLabel(new ImageIcon(imgOne));
        lbIm2 = new JLabel(new ImageIcon(imgTwo));

        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.HORIZONTAL;
        c.anchor = GridBagConstraints.CENTER;
        c.weightx = 0.5;
        c.gridx = 0;
        c.gridy = 0;

        c.fill = GridBagConstraints.HORIZONTAL;
        c.gridx = 0;
        c.gridy = 1;
        frame.getContentPane().add(lbIm1, c);

        c.fill = GridBagConstraints.HORIZONTAL;
        c.gridx = 1;
        c.gridy = 1;
        frame.getContentPane().add(lbIm2, c);

        frame.pack();
        frame.setVisible(true);

        update_lbIm2(source, q, d, l);
        System.out.println("done!");
    }

    public static void main(String[] args) throws IOException, Exception {
            ImageDisplay ren = new ImageDisplay();
            ren.showIms(args);
    }

}
