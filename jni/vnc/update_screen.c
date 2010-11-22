#define OUT_T CONCAT3E(uint,OUT,_t)
#define FUNCTION CONCAT2E(update_screen_,OUT)




void FUNCTION(void)
{  
  static int i,j;
  
 update_fb_info();
  
// //   detect active buffer
//      offset= (scrinfo.xoffset) * (scrinfo.bits_per_pixel/8) +
//                        (scrinfo.yoffset) * scrinfo.xres;
//   if (scrinfo.yoffset)
//     offset=scrinfo.xres*scrinfo.yoffset;
//   else
//     offset=0;
//  offset = scrinfo.xres * scrinfo.yoffset +  scrinfo.xoffset * scrinfo.bits_per_pixel / CHAR_BIT;

 
  OUT_T* a = (OUT_T*)cmpbuf;
  OUT_T* b = (OUT_T*)fbmmap;
  
  int max_x=-1,max_y=-1, min_x=9999, min_y=9999;
  idle=1;
 
   
#if 1
  if (rotation==0)
  { 
   //memcpy(vncbuf,fbmmap,vncscr->width*vncscr->height*scrinfo.bits_per_pixel/CHAR_BIT);
	for (j = 0; j < scrinfo.yres; j++)
	{
	  for (i = 0; i < scrinfo.xres; i++)
	    {
     	 int offset = j * scrinfo.xres;
		 int pixelToVirtual = (j+scrinfo.yoffset)*scrinfo.xres_virtual+i+scrinfo.xoffset;
		if (a[i + offset]!=b[pixelToVirtual])
		{
		  a[i + offset]=b[pixelToVirtual];

		  if (i>max_x)
		    max_x=i;
		  if (i<min_x)
		    min_x=i;
		  
		  if (j>max_y)
		    max_y=j;
		  if (j<min_y)
		    min_y=j;
		  
		  if (idle)
		    idle=0;
		}
	    }
	}
  }
  else if (rotation==90)
  {
	for (j = 0; j < scrinfo.yres; j++)
	{
	  int offset = i * scrinfo.yres;
 int pixelToVirtual = (j+scrinfo.yoffset)*scrinfo.xres_virtual+i+scrinfo.xoffset;
	  for (i = 0; i < scrinfo.xres; i++)
		{
		  if (a[(scrinfo.yres - 1 - j + offset)] != b[pixelToVirtual])
		   {
		  a[(scrinfo.yres - 1 - j + offset)] = b[pixelToVirtual];
		  
		  if (i>max_y)
		    max_y=i;
		  if (i<min_y)
		    min_y=i;
		  
		  int h=scrinfo.yres-j;
		  
		  if (h < min_x)
		    min_x=scrinfo.yres-j;
 		  if (h > max_x)
		    max_x=scrinfo.yres-j;
		  
		  if (idle)
		    idle=0;
		  }
		}
	}
  }
  else if (rotation==180)
  {
        for (j = 0; j < scrinfo.yres; j++)
	{
		int offset = (scrinfo.yres - 1 - j) * scrinfo.xres;
 int pixelToVirtual = (j+scrinfo.yoffset)*scrinfo.xres_virtual+i+scrinfo.xoffset;
		for (i = 0; i < scrinfo.xres; i++)
		{
		  if (a[((scrinfo.xres - 1 - i) + offset )]!=b[pixelToVirtual])
		  {
		  a[((scrinfo.xres - 1 - i) + offset )]=b[pixelToVirtual];
		    
		    
		  if (i>max_x)
		    max_x=i;
		  if (i<min_x)
		    min_x=i;
		  
		  int h=scrinfo.yres-j;
		  
		  if (h < min_y)
		    min_y=scrinfo.yres-j;
 		  if (h > max_y)
		    max_y=scrinfo.yres-j;
		  
		if (idle)
		    idle=0;
		  }
		}
	}
  }
  else if (rotation==270)
  {
    	for (j = 0; j < scrinfo.yres; j++)
	{
	  int offset = (scrinfo.xres - 1 - i) * scrinfo.yres;
 		int pixelToVirtual = (j+scrinfo.yoffset)*scrinfo.xres_virtual+i+scrinfo.xoffset;
		for (i = 0; i < scrinfo.xres; i++)
		{
		    if(a[j + offset] != b[pixelToVirtual])
		    {
		      a[j + offset] = b[pixelToVirtual];
		       
	          if (i>max_y)
		    max_y=i;
		  if (i<min_y)
		    min_y=i;
		  
		  if (j < min_x)
		    min_x=j;
 		  if (j > max_x)
		    max_x=j;
		  
		  if (idle)
		    idle=0;
		  }
		}
	}
	
}
  
memcpy(vncbuf,a,vncscr->width*vncscr->height*scrinfo.bits_per_pixel/CHAR_BIT);
#else 
memcpy(vncbuf,b,vncscr->width*vncscr->height*scrinfo.bits_per_pixel/CHAR_BIT);
max_x = scrinfo.xres - 1;
max_y = scrinfo.yres - 1;
idle=0;
#endif

  if (min_x!=9999 && min_y!=9999 && max_x!=-1 && max_y!=-1)
  {
     max_x++;
     max_y++;
     
  rfbMarkRectAsModified(vncscr, min_x, min_y, max_x, max_y);
  
  } 

  if (idle)
  {
    standby=standby+1;
//__android_log_print(ANDROID_LOG_INFO,"VNC","standby %d xoff=%d yoff=%d offset=%d\n",standby,scrinfo.xoffset,scrinfo.yoffset,PIXEL_TO_VIRTUALPIXEL(i,j));
    change=0;
  }
  else
  {
    change=change+1;
    standby=0;
//        __android_log_print(ANDROID_LOG_INFO,"VNC","change %d\tmin_x=%d max_x=%d min_y=%d max_y=%d xoff=%d yoff=%d offset=%d\n",change,min_x,max_x,min_y,max_y,scrinfo.xoffset,scrinfo.yoffset,PIXEL_TO_VIRTUALPIXEL(i,j));
  } 
}
