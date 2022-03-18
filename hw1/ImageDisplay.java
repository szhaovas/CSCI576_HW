import java.awt.*;
import java.awt.image.*;
import java.io.*;
import java.util.Arrays;
import javax.swing.*;


public class ImageDisplay {

	JFrame frame;
	JLabel lbIm1;
	BufferedImage imgOne;
	int width = 512; // default image width and height
	int height = 512;

        class IntImage {

            int w;
            int h;
            int[][][] data;

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
        }

        IntImage zero_pad(IntImage int_image, int radius) {
            int[][][] image = int_image.data;
            int w = int_image.w;
            int h = int_image.h;
            int[][][] result = new int[h+2*radius][w+2*radius][3];

            for (int r=0; r<h; r++) {
                for (int c=0; c<w; c++) {
                    result[r+radius][c+radius][0] = image[r][c][0];
                    result[r+radius][c+radius][1] = image[r][c][1];
                    result[r+radius][c+radius][2] = image[r][c][2];
                }
            }

            return new IntImage(result);
        }

        IntImage reSample(IntImage int_image, double s) {
            assert 0 < s && s <= 1;
            if (s == 1) {
                return int_image;
            }

            int[][][] image = int_image.data;
            int w = int_image.w;
            int h = int_image.h;
            IntImage padded_img = zero_pad(int_image, 1);
            int[][][] padded_matrix = padded_img.data;

            int new_width = (int) Math.round(w*s);
            int new_height = (int) Math.round(h*s);
            int[][][] result = new int[h][w][3];

            for (int r=0; r<new_height; r++) {
                for (int c=0; c<new_width; c++) {
                    int sample_r = (int) Math.round(r/s);
                    int sample_c = (int) Math.round(c/s);

                    double sum_r = 0;
                    double sum_g = 0;
                    double sum_b = 0;
                    for (int conv_r=sample_r; conv_r<=sample_r+2; conv_r++) {
                        for (int conv_c=sample_c; conv_c<=sample_c+2; conv_c++) {
//                            1/9 ~= 0.1111
                            sum_r += padded_matrix[conv_r][conv_c][0] * 0.1111;
                            sum_g += padded_matrix[conv_r][conv_c][1] * 0.1111;
                            sum_b += padded_matrix[conv_r][conv_c][2] * 0.1111;
                        }
                    }
                    result[r][c][0] = (int) Math.round(sum_r);
                    result[r][c][1] = (int) Math.round(sum_g);
                    result[r][c][2] = (int) Math.round(sum_b);
                }
            }

            return new IntImage(result);
        }

        double[] getUniSteps(int nitvls, double low, double high) {
            assert high >= low;
            double[] result = new double[nitvls+1];
            double interval_size = (high - low) / nitvls;

            double curr_step = low;
            for (int i=0; i<nitvls; i++) {
                result[i] = curr_step;
                curr_step += interval_size;
            }
            result[nitvls] = high;

            return result;
        }

        double[] getLogSteps(int nitvls, double pivot) {
            assert 0 <= pivot && pivot <= 255;
            int nintvls_low = (int) Math.round(nitvls * (pivot/255));
            int nintvls_high = nitvls - nintvls_low;

            double[] low_uni_steps = getUniSteps(nintvls_low, 0, pivot);
            double[] high_uni_steps = getUniSteps(nintvls_high, pivot, 255);
            double[] result = new double[nitvls+1];

            int i = 0;
            for (double s : low_uni_steps) {
                double norm_s = (pivot-s)/pivot;
                double invMu_scaled = (Math.pow(2,8*norm_s)-1)/256;
                result[i] = pivot - pivot*invMu_scaled;
                i++;
            }
//            both low_uni_steps and high_uni_steps contain pivot
            i--;
            for (double s : high_uni_steps) {
                double norm_s = (s-pivot)/(255-pivot);
                double invMu_scaled = (Math.pow(2,8*norm_s)-1)/256;
                result[i] = pivot + (255-pivot)*invMu_scaled;
                i++;
            }

            return result;
        }

        int roundToMid(double x, double[] steps) {
            for (int i=0; i<steps.length-1; i++) {
                double low = steps[i];
                double high = steps[i+1];
                if ((x >= low) && (x <= high)) {
                    return (int) Math.round((low+high)/2);
                }
            }
            return 255;
        }

        IntImage reQuantize(IntImage int_image, int q, int m) {
            assert 1 <= q && q <= 8;
            assert -1 <= m && m <= 255;
            int[][][] image = int_image.data;
            int w = int_image.w;
            int h = int_image.h;
            int[][][] result = new int[h][w][3];

            int nintvls = (int) Math.pow(2,q);
            double[] steps = new double[nintvls+1];
            if (m == -1) {
                steps = getUniSteps(nintvls, 0, 255);
            } else {
                steps = getLogSteps(nintvls, m);
            }

            System.out.println("Steps are: " + Arrays.toString(steps));

            for (int r=0; r<h; r++) {
                for (int c=0; c<w; c++) {
                    result[r][c][0] = roundToMid(image[r][c][0], steps);
                    result[r][c][1] = roundToMid(image[r][c][1], steps);
                    result[r][c][2] = roundToMid(image[r][c][2], steps);
                }
            }

            return new IntImage(result);
        }

	/** Read Image RGB
	 *  Reads the image of given width and height at the given imgPath into the provided BufferedImage.
	 */
	private void readImageRGB(int width, int height, String imgPath, BufferedImage img, double s, int q, int m)
	{
		try
		{
			int frameLength = width*height*3;

			File file = new File(imgPath);
			RandomAccessFile raf = new RandomAccessFile(file, "r");
			raf.seek(0);

			long len = frameLength;
			byte[] bytes = new byte[(int) len];

			raf.read(bytes);

                        IntImage original = new IntImage(bytes, width, height);
                        IntImage down_sized = reSample(original, s);
                        IntImage quantized = reQuantize(down_sized, q, m);
                        byte[] display = quantized.ByteImage();

			int ind = 0;
			for(int y = 0; y < height; y++)
			{
				for(int x = 0; x < width; x++)
				{
					byte a = 0;
					byte r = display[ind];
					byte g = display[ind+height*width];
					byte b = display[ind+height*width*2];

					int pix = 0xff000000 | ((r & 0xff) << 16) | ((g & 0xff) << 8) | (b & 0xff);
					//int pix = ((a << 24) + (r << 16) + (g << 8) + b);
					img.setRGB(x,y,pix);
					ind++;
				}
			}
		}
		catch (FileNotFoundException e)
		{
			e.printStackTrace();
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}
	}

	public void showIms(String[] args){

		// Read a parameter from command line
                double s = Double.parseDouble(args[1]);
                int q = Integer.parseInt(args[2]);
                int m = Integer.parseInt(args[3]);

		// Read in the specified image
		imgOne = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		readImageRGB(width, height, args[0], imgOne, s, q, m);

		// Use label to display the image
		frame = new JFrame();
		GridBagLayout gLayout = new GridBagLayout();
		frame.getContentPane().setLayout(gLayout);

		lbIm1 = new JLabel(new ImageIcon(imgOne));

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

		frame.pack();
		frame.setVisible(true);
	}

	public static void main(String[] args) {
		ImageDisplay ren = new ImageDisplay();
		ren.showIms(args);
	}

}
