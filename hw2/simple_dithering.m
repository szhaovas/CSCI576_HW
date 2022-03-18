source = [[1,2,3,4,5,6,7,8,9,0,1,2];
          [0,1,2,3,4,5,6,7,8,9,0,1];
          [9,0,1,2,3,4,5,6,7,8,9,0];
          [8,9,0,1,2,3,4,5,6,7,8,9];
          [7,8,9,0,1,2,3,4,5,6,7,8];
          [6,7,8,9,0,1,2,3,4,5,6,7];
          [5,6,7,8,9,0,1,2,3,4,5,6];
          [4,5,6,7,8,9,0,1,2,3,4,5]];
  
source_disp = to0255(source);
figure;
imshow(source_disp);
fixed_thres = fixed_threshold(source, 4.5);
fixed_thres_disp = to0255(fixed_thres);
figure;
imshow(fixed_thres_disp);
D = [[6,8,4];[1,0,3];[5,2,7]];
dt_thres = dither(source, D);
dt_thres_disp = to0255(dt_thres);
figure;
imshow(dt_thres_disp);
dt_thres_1 = dither(source, D, 1);
dt_thres_1_disp = to0255(dt_thres_1);
figure;
imshow(dt_thres_1_disp);
%%
function img = to0255(source)
    [nrows, ncols] = size(source);
    img = zeros(nrows, ncols, 'uint8');
    scale = 255/9;
    
    for r=1:nrows
        for c=1:ncols
            img(r,c) = 255 - round(source(r,c)*scale);
        end
    end
end

function new_img = fixed_threshold(img, threshold)
    [nrows, ncols] = size(img);
    new_img = zeros(nrows, ncols, 'uint8');
    
    for r=1:nrows
        for c=1:ncols
            if img(r,c) <= threshold
                new_img(r,c) = 0;
            else
                new_img(r,c) = 9;
            end
        end
    end
end

function new_img = dither(img, mat, offset)
    if nargin == 2
        offset = 0;
    end
    [nrows, ncols] = size(img);
    [sz, ~] = size(mat);
    new_img = zeros(nrows, ncols, 'uint8');
    
    for i=1:nrows
        for j=1:ncols
            if img(i,j) > mat(mod(i+offset,sz)+1,mod(j+offset,sz)+1)
                new_img(i,j) = 9;
            end
        end
    end
end