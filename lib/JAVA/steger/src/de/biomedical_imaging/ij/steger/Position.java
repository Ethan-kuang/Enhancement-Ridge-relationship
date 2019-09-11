/*  detect-lines, extract lines and their width from images.
    Copyright (C) 1996-1998 Carsten Steger

    This program is free software; you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation; either version 2, or (at your option)
    any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program; if not, write to the Free Software
    Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA. */

/* 	Changes Made by R. Balasubramanian for incorporating the the detect lines code to incorporate
   	within GRASP (May 10th 1999) */

/*	Translated into an ImageJ java plugin by Thorsten Wagner (Dez. 2014) */

package de.biomedical_imaging.ij.steger;

import org.apache.commons.lang3.mutable.MutableDouble;
import org.apache.commons.lang3.mutable.MutableLong;




public class Position {
	/* The pixel boundaries need to be enlarged slightly since in practice it
	   frequently happens for neighboring pixels a and b that pixel a says a
	   maximum lies within pixel b and vice versa.  This presents no problem since
	   linking algoritm will take care of this. */
	private static final double PIXEL_BOUNDARY = 0.6;
	
	/** Solve the linear equation a*x+b=0 and return the result in t and the number
	   of solutions in num. **/
	public void solve_linear(double a,double b, MutableDouble t, MutableLong num)
	{
		
		if (a==0.0) { //
			num.setValue(0);
	    return;
	  } else {
		num.setValue(1);
	    t.setValue(-b/a);
	    return;
	  }
	}
	
	
	/** Compute the eigenvalues and eigenvectors of the Hessian matrix given by
	   dfdrr, dfdrc, and dfdcc, and sort them in descending order according to
	   their absolute values. **/
	public void compute_eigenvals(double dfdrr,double dfdrc, double dfdcc, double[] eigval, double[][] eigvec)
	{
	  double theta, t, c, s, e1, e2, n1, n2; /* , phi; */

	  /* Compute the eigenvalues and eigenvectors of the Hessian matrix. */
	  if (dfdrc != 0.0) {
	    theta = 0.5*(dfdcc-dfdrr)/dfdrc;
	    t = 1.0/(Math.abs(theta)+Math.sqrt(theta*theta+1.0));
	    if (theta < 0.0) t = -t;
	    c = 1.0/Math.sqrt(t*t+1.0);
	    s = t*c;
	    e1 = dfdrr-t*dfdrc;
	    e2 = dfdcc+t*dfdrc;
	  } else {
	    c = 1.0;
	    s = 0.0;
	    e1 = dfdrr;
	    e2 = dfdcc;
	  }
	  n1 = c;
	  n2 = -s;

	  /* If the absolute value of an eigenvalue is larger than the other, put that
	     eigenvalue into first position.  If both are of equal absolute value, put
	     the negative one first. */
	  if (Math.abs(e1) > Math.abs(e2)) {
	    eigval[0] = e1;
	    eigval[1] = e2;
	    eigvec[0][0] = n1;
	    eigvec[0][1] = n2;
	    eigvec[1][0] = -n2;
	    eigvec[1][1] = n1;
	  } else if (Math.abs(e1) < Math.abs(e2)) {
	    eigval[0] = e2;
	    eigval[1] = e1;
	    eigvec[0][0] = -n2;
	    eigvec[0][1] = n1;
	    eigvec[1][0] = n1;
	    eigvec[1][1] = n2;
	  } else {
	    if (e1 < e2) {
	      eigval[0] = e1;
	      eigval[1] = e2;
	      eigvec[0][0] = n1;
	      eigvec[0][1] = n2;
	      eigvec[1][0] = -n2;
	      eigvec[1][1] = n1;
	    } else {
	      eigval[0] = e2;
	      eigval[1] = e1;
	      eigvec[0][0] = -n2;
	      eigvec[0][1] = n1;
	      eigvec[1][0] = n1;
	      eigvec[1][1] = n2;
	    }
	  }
	}
	
    @SuppressWarnings("unused")
	private void print_ascii2(byte[] image, long width, long height)
    {
		int i = 0;
		int j = 0;
		long k = 0;
		for(i=0; i < height; i++){
			for(j=0; j < width;j++){
				k=LinesUtil.LINCOOR(i,j,width);
				System.out.print(""+image[(int) k]+" ");
			}
			System.out.print("\n");
		}
	}
    
    @SuppressWarnings("unused")
	private void print_ascii2(float[] image, long width, long height)
    {
		int i = 0;
		int j = 0;
		long k = 0;
		for(i=0; i < height; i++){
			for(j=0; j < width;j++){
				k=LinesUtil.LINCOOR(i,j,width);
				System.out.print(""+(int)image[(int) k]+" \t");
			}
			System.out.print("\n");
		}
	}
	
	/* For each point in the image determine whether there is a local maximum of
	   the second directional derivative in the direction (nx[l],ny[l]) within the
	   pixels's boundaries.  If so, set ismax[l] to 2 if the eigenvalue ev[l] is
	   larger than high, to 1 if ev[l] is larger than low, and to 0 otherwise.
	   Furthermore, put the sub-pixel position of the maximum into (px[l],py[l]).
	   The parameter mode determines whether maxima (dark lines points) or minima
	   (bright line points) should be selected.  The partial derivatives of the
	   image are input as ku[]. */
	private void compute_line_points(float[][] ku, byte[] ismax, float[] ev,float[] nx, float[] ny,float[] px, float[] py, long width, long height, double low,double high, long mode)
	{
	  long    r, c, l;
	  double[]  k = new double[5];
	  double[]  eigval = new double[2];
	  double[][]  eigvec = new double[2][2];
	  double  a, b;
	  MutableDouble t = new MutableDouble();
	  MutableLong  num = new MutableLong();
	  double  n1, n2;
	  double  p1, p2;
	  double  val;

	  for (r=0; r<height; r++) {
	    for (c=0; c<width; c++) {
	      l = LinesUtil.LINCOOR(r,c,width);
	      k[0] = ku[0][(int) l];
	      k[1] = ku[1][(int) l];
	      k[2] = ku[2][(int) l];
	      k[3] = ku[3][(int) l];
	      k[4] = ku[4][(int) l];
	      ev[(int) l] = (float) 0.0;
	      nx[(int) l] = (float) 0.0;
	      ny[(int) l] = (float) 0.0;
	      compute_eigenvals(k[2],k[3],k[4],eigval,eigvec);
	      if (mode == LinesUtil.MODE_LIGHT)
	        val = -eigval[0];
	      else
	        val = eigval[0];
	      if (val > 0.0) {
	        ev[(int) l] = (float) val;
	        n1 = eigvec[0][0];
	        n2 = eigvec[0][1];
	        a = k[2]*n1*n1+2.0*k[3]*n1*n2+k[4]*n2*n2;
	        b = k[0]*n1+k[1]*n2;
	        solve_linear(a,b,t,num);
	        if (num.intValue() != 0) {
	          p1 = t.doubleValue()*n1;
	          p2 = t.doubleValue()*n2;
	          if (Math.abs(p1) <= PIXEL_BOUNDARY && Math.abs(p2) <= PIXEL_BOUNDARY) {
	            if (val >= low) {
	              if (val >= high)
	                ismax[(int) l] = 2;
	              else
	                ismax[(int) l] = 1;
	            }
	            nx[(int) l] = (float) n1;
	            ny[(int) l] = (float) n2;
	            px[(int) l] = (float) (r+p1);
	            py[(int) l] = (float) (c+p2);
	          }
	        }
	      }
	    }
	  }
	}
	
	/* Main routine to detect lines in an image of dimension width * height.  The
	   extracted lines are returned in result, while num_result is the number of
	   detected lines.  The parameter sigma is the amount of smoothing that the
	   Gaussian kernel performs, while low and high are the hysteresis thresholds
	   used in the linking algorithm.  With mode, either bright or dark lines can
	   be selected.  The parameter compute_width determines whether the line width
	   should be extracted, while correct_pos determines whether the line width
	   and position correction should be applied. */
	public void detect_lines(float[] image,long width, long height, Lines contours, MutableLong num_result, double sigma, double low, double high, long mode, boolean compute_width, boolean correct_pos,boolean extend_lines, Junctions junctions)
	{
	  byte[] ismax;
	  float[] ev, n1, n2, p1, p2;
	  float[][] k = new float[5][(int) (width*height)];
	  
	//  for (i=0;i<5;i++)
	//    k[i] = xcalloc(width*height,sizeof(float));
	  Convol convol = new Convol();
	  convol.convolve_gauss(image,k[0],width,height,sigma,LinesUtil.DERIV_R);
	  convol.convolve_gauss(image,k[1],width,height,sigma,LinesUtil.DERIV_C);
	  convol.convolve_gauss(image,k[2],width,height,sigma,LinesUtil.DERIV_RR);
	  convol.convolve_gauss(image,k[3],width,height,sigma,LinesUtil.DERIV_RC);
	  
	  convol.convolve_gauss(image,k[4],width,height,sigma,LinesUtil.DERIV_CC);
	
	  ismax = new byte[(int) (width*height)];
	  ev = new float[(int) (width*height)];
	  n1 = new float[(int) (width*height)];
	  n2 = new float[(int) (width*height)];
	  p1 = new float[(int) (width*height)];
	  p2 = new float[(int) (width*height)];
	  /*
	   * The C library function void *memset(void *str, int c, size_t n) 
	   * copies the character c (an unsigned char) to the first n characters 
	   * of the string pointed to by the argument str.
	   */
	 // memset(ismax,0,width*height*sizeof(*ismax));
	  // memset(ev,0,width*height*sizeof(*ev));
	  for(int j = 0; j < ismax.length; j++){
		  ev[j] = 0;
		  ismax[j] = 0;
	  }

	  compute_line_points(k,ismax,ev,n1,n2,p1,p2,width,height,low,high,mode);
	  
	  Link l = new Link();
	  l.compute_contours(ismax,ev,n1,n2,p1,p2,k[0],k[1],contours,num_result,sigma,
	                   extend_lines,(int)mode,low,high,width,height,junctions);
	  Width w = new Width();
	  if (compute_width)
	    w.compute_line_width(k[0],k[1],width,height,sigma,mode,correct_pos,contours,
	                       num_result);

	}


}
