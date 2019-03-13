#include <stdio.h>
#include <stdlib.h>
#include <string.h>

typedef unsigned char byte;

#define RGB24_BYTES_IN_PIXEL  3
#define RGB24_BITS_IN_PIXEL  24

static void
getBmpFileHeader(byte* const pbBmpFileHeader, // bmp文件头14个字节
                 const   int iWidth, 
                 const   int iHeight, 
                 const   int iBytesInPixel)
{
    // 2bytes - 说明文件的类型，该值必需是 0x42 0x4D ，也就是字符'BM'，否则表示根本不是BMP
    pbBmpFileHeader[0] = 'B';
    pbBmpFileHeader[1] = 'M';

    // 4bytes - 说明该位图文件的大小，用字节为单位(14字节文件头，40字节位图信息头，3字节RGB)
    const int iFileSize = 14 + 40 + (iWidth * iHeight * iBytesInPixel);
    for (int idx = 0;
             idx < 4;
             idx++)
    {
        pbBmpFileHeader[2 + idx] = (byte)(iFileSize >> (idx * 8));
    }

    // 2bytes - 保留，必须设置为0
    pbBmpFileHeader[6] = 0;
    pbBmpFileHeader[7] = 0;

    // 2bytes - 保留，必须设置为0
    pbBmpFileHeader[8] = 0;
    pbBmpFileHeader[9] = 0;

    // 4bytes - 说明从文件头开始到实际的图像数据之间的字节的偏移量
    pbBmpFileHeader[10] = 54;
    pbBmpFileHeader[11] = 0;
    pbBmpFileHeader[12] = 0;
    pbBmpFileHeader[13] = 0;
}

static void 
getBmpInfoHeader(byte* const pbBmpInfoHeader, // 位图信息头40个字节
                 const   int width, 
                 const   int height,
                 const   int iBitsInPixel)
{
    // 4bytes - 说明 BitmapInfoHeader 结构所需要的字节数
    pbBmpInfoHeader[0] = 40;
    pbBmpInfoHeader[1] = 0;
    pbBmpInfoHeader[2] = 0;
    pbBmpInfoHeader[3] = 0;

    // 4bytes - 说明图像的宽度，以像素为单位
    for (int idx = 0;
             idx < 4;
             idx++)
    {
        pbBmpInfoHeader[4 + idx] = (byte)(width >> (idx * 8));
    }

    // 4bytes - 说明图像的高度，以像素为单位
    // 注：这个值除了用于描述图像的高度之外，它还有另一个用处，就是指明该图像是倒向的位图，还是正向的位图。
    // 如果该值是一个正数，说明图像是倒向的，即：数据的第一行其实是图像的最后一行。
    // 如果该值是一个负数，则说明图像是正向的。
    // 大多数的BMP文件都是倒向的位图，也就是时，高度值是一个正数。
    for (int idx = 0;
             idx < 4;
             idx++)
    {
        pbBmpInfoHeader[8 + idx] = (byte)(height >> (idx * 8));
    }

    // 2bytes - 表示bmp图片的平面属，显然显示器只有一个平面，所以恒等于1
    pbBmpInfoHeader[12] = 1;
    pbBmpInfoHeader[13] = 0;

    // 2bytes - 说明比特数/像素，其值为1、4、8、16、24、32
    pbBmpInfoHeader[14] = iBitsInPixel;
    pbBmpInfoHeader[15] = 0;

    // 4bytes - 说明图像数据压缩的类型，其中：
    // 0 - BI_RGB       - 没有压缩
    // 1 - BI_RLE8      - 每个像素8比特的RLE压缩编码，压缩格式由2字节组成(重复像素计数和颜色索引)；
    // 2 - BI_RLE4      - 每个像素4比特的RLE压缩编码，压缩格式由2字节组成
    // 3 - BI_BITFIELDS - 每个像素的比特由指定的掩码决定
    // 4 - BI_JPEG      - JPEG格式
    // 5 - BI_PNG       - PNG格式
    pbBmpInfoHeader[16] = 0;
    pbBmpInfoHeader[17] = 0;
    pbBmpInfoHeader[18] = 0;
    pbBmpInfoHeader[19] = 0;

    // 4bytes - 说明图像的大小，以字节为单位。当用BI_RGB格式时，可设置为0。
    pbBmpInfoHeader[20] = 0;
    pbBmpInfoHeader[21] = 0;
    pbBmpInfoHeader[22] = 0;
    pbBmpInfoHeader[23] = 0;

    // 4bytes - 说明水平分辨率，用像素/米表示。
    pbBmpInfoHeader[24] = 0;
    pbBmpInfoHeader[25] = 0;
    pbBmpInfoHeader[26] = 0;
    pbBmpInfoHeader[27] = 0;

    // 4bytes - 说明垂直分辨率，用像素/米表示。
    pbBmpInfoHeader[28] = 0;
    pbBmpInfoHeader[29] = 0;
    pbBmpInfoHeader[30] = 0;
    pbBmpInfoHeader[31] = 0;

    // 4bytes - 说明位图实际使用的彩色表中的颜色索引数（设为0的话，则说明使用所有调色板项）。
    pbBmpInfoHeader[32] = 0;
    pbBmpInfoHeader[33] = 0;
    pbBmpInfoHeader[34] = 0;
    pbBmpInfoHeader[35] = 0;

    // 4bytes - 说明对图像显示有重要影响的颜色索引的数目，如果是0，表示都重要。
    pbBmpInfoHeader[36] = 0;
    pbBmpInfoHeader[37] = 0;
    pbBmpInfoHeader[38] = 0;
    pbBmpInfoHeader[39] = 0;
}

#define BMP200X200ByteSize 200 * 200 * 3
#define BMP640X480ByteSize 640 * 480 * 3

#define RAW_BMP_WIDTH  640
#define RAW_BMP_HEIGHT 480

#define PATCH_BMP_WIDTH  200
#define PATCH_BMP_HEIGHT 200

int
main(int   argc, 
     char* argv[])
{
    FILE* fpRawBmpFile     = NULL;
    FILE* fp200X200BmpFile = NULL;

    byte* pb200X200Bmp = NULL;
    byte* pb640X480Bmp = NULL;

    int iResult = EXIT_SUCCESS;
    do
    {
        fpRawBmpFile = fopen(argv[1], "rb");

        if (NULL == fpRawBmpFile) {
            printf("%s can't open!\n", argv[1]);
            iResult = EXIT_FAILURE;
            break;
        }

        pb200X200Bmp = (byte*)malloc(BMP200X200ByteSize);
        memset(pb200X200Bmp, 0, BMP200X200ByteSize);

        pb640X480Bmp = (byte*)malloc(BMP640X480ByteSize);
        memset(pb640X480Bmp, 0, BMP640X480ByteSize);

        byte caBmpFileHeader[14];
        memset(caBmpFileHeader, 0, sizeof(caBmpFileHeader));

        byte caBmpInfoHeader[40];
        memset(caBmpInfoHeader, 0, sizeof(caBmpInfoHeader));

        fread(caBmpFileHeader, sizeof(caBmpFileHeader), 1, fpRawBmpFile);
        fread(caBmpInfoHeader, sizeof(caBmpInfoHeader), 1, fpRawBmpFile);
        fread(pb640X480Bmp, BMP640X480ByteSize, 1, fpRawBmpFile);
        fclose(fpRawBmpFile);
        fpRawBmpFile = NULL;

        const int rowStart = (RAW_BMP_HEIGHT - PATCH_BMP_HEIGHT) / 2;
        const int colStart = (RAW_BMP_WIDTH  - PATCH_BMP_WIDTH)  / 2;

        int iCurPos = 0;

        for (int idxRow = 0;
                 idxRow < PATCH_BMP_HEIGHT;
                 idxRow++)
        {
            int cutPixelPos = ((rowStart + idxRow) * RAW_BMP_WIDTH) + colStart;

            memcpy(pb200X200Bmp + iCurPos, 
                   pb640X480Bmp + (cutPixelPos * RGB24_BYTES_IN_PIXEL),
                   PATCH_BMP_WIDTH * RGB24_BYTES_IN_PIXEL);

            iCurPos += PATCH_BMP_WIDTH * RGB24_BYTES_IN_PIXEL;
        }

        fp200X200BmpFile = fopen(argv[2], "wb");

        if (NULL == fp200X200BmpFile) {
            printf("%s can't open!\n", argv[2]);
            iResult = EXIT_FAILURE;
            break;
        }

        memset(caBmpFileHeader, 0, sizeof(caBmpFileHeader));
        getBmpFileHeader(caBmpFileHeader, PATCH_BMP_WIDTH, PATCH_BMP_HEIGHT, RGB24_BYTES_IN_PIXEL);
        fwrite(caBmpFileHeader, sizeof(caBmpFileHeader), 1, fp200X200BmpFile);

        memset(caBmpInfoHeader, 0, sizeof(caBmpInfoHeader));
        getBmpInfoHeader(caBmpInfoHeader, PATCH_BMP_WIDTH, -PATCH_BMP_HEIGHT, RGB24_BITS_IN_PIXEL);
        fwrite(caBmpInfoHeader, sizeof(caBmpInfoHeader), 1, fp200X200BmpFile);

        fwrite(pb200X200Bmp, BMP200X200ByteSize, 1, fp200X200BmpFile);

        fclose(fp200X200BmpFile);
        fp200X200BmpFile = NULL;

    }while(0);

    if (NULL != fpRawBmpFile) {
        fclose(fpRawBmpFile);
        fpRawBmpFile = NULL;
    }

    if (NULL != fp200X200BmpFile) {
        fclose(fp200X200BmpFile);
        fp200X200BmpFile = NULL;
    }

    if (NULL != pb200X200Bmp) {
        free(pb200X200Bmp);
        pb200X200Bmp = NULL;
    }

    if (NULL != pb640X480Bmp) {
        free(pb640X480Bmp);
        pb640X480Bmp = NULL;
    }

    return iResult;
}