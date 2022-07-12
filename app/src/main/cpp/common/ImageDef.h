//
// Created by ByteFlow on 2019/7/10.
//

#ifndef FFMPEGDECODER_IMAGEDEF_H
#define FFMPEGDECODER_IMAGEDEF_H

#include <malloc.h>
#include <string.h>
#include <unistd.h>
#include "stdio.h"
#include "sys/stat.h"
#include "stdint.h"
#include <CommonTools.h>

#define IMAGE_FORMAT_RGBA           0x01

#define IMAGE_FORMAT_RGBA_EXT       "RGB32"

typedef struct _tag_NativeImage
{
	int width;
	int height;
	int format;
	uint8_t *ppPlane[1];

	_tag_NativeImage()
	{
		width = 0;
		height = 0;
		format = 0;
		ppPlane[0] = NULL;
	}
} NativeImage;

class NativeImageUtil
{
public:
	static void AllocNativeImage(NativeImage *pImage)
	{
		if (pImage->height == 0 || pImage->width == 0) return;

		switch (pImage->format)
		{
			case IMAGE_FORMAT_RGBA:
			{
				pImage->ppPlane[0] = static_cast<uint8_t *>(malloc(pImage->width * pImage->height * 4));
			}
			default:
				break;
		}
	}

	static void FreeNativeImage(NativeImage *pImage)
	{
		if (pImage == NULL || pImage->ppPlane[0] == NULL) return;

		free(pImage->ppPlane[0]);
		pImage->ppPlane[0] = NULL;
	}

	static void CopyNativeImage(NativeImage *pSrcImg, NativeImage *pDstImg)
	{
		if(pSrcImg == NULL || pSrcImg->ppPlane[0] == NULL) return;

		if(pSrcImg->format != pDstImg->format ||
		   pSrcImg->width != pDstImg->width ||
		   pSrcImg->height != pDstImg->height) return;

		if(pDstImg->ppPlane[0] == NULL) AllocNativeImage(pDstImg);

		switch (pSrcImg->format)
		{
			case IMAGE_FORMAT_RGBA:
			{
				memcpy(pDstImg->ppPlane[0], pSrcImg->ppPlane[0], pSrcImg->width * pSrcImg->height * 4);
			}
				break;
			default:
			{
			}
				break;
		}

	}

	static void DumpNativeImage(NativeImage *pSrcImg, const char *pPath, const char *pFileName)
	{
		if (pSrcImg == NULL || pPath == NULL || pFileName == NULL) return;

		if(access(pPath, 0) == -1)
		{
			mkdir(pPath, 0666);
		}

		char imgPath[256] = {0};
		const char *pExt = NULL;
		switch (pSrcImg->format)
		{
			case IMAGE_FORMAT_RGBA:
				pExt = IMAGE_FORMAT_RGBA_EXT;
				break;

			default:
				break;
		}

		sprintf(imgPath, "%s/IMG_%dx%d_%s.%s", pPath, pSrcImg->width, pSrcImg->height, pFileName, pExt);

		FILE *fp = fopen(imgPath, "wb");

		if(fp)
		{
			switch (pSrcImg->format)
			{
				case IMAGE_FORMAT_RGBA:
				{
					fwrite(pSrcImg->ppPlane[0],
						   static_cast<size_t>(pSrcImg->width * pSrcImg->height * 4), 1, fp);
					break;
				}
				default:
				{
					break;
				}
			}

			fclose(fp);
			fp = NULL;
		}


	}
};


#endif //FFMPEGDECODER_IMAGEDEF_H
