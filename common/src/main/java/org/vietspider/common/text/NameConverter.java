package org.vietspider.common.text;

public class NameConverter { 
  
  public static synchronized String decode(String value){   
    char [] ch = value.toCharArray();
    return decode(ch, 0, ch.length, new char[1024]);
  }
  
  synchronized static String decode(char[] in, int off, int len, char[] convtBuf) {
    if (convtBuf.length < len) {
      int newLen = len * 2;
      if (newLen < 0) {
        newLen = Integer.MAX_VALUE;
      } 
      convtBuf = new char[newLen];
    }
    char aChar;
    char[] out = convtBuf; 
    int outLen = 0;
    int end = off + len;

    while (off < end) {
      aChar = in[off++];
      if (aChar == '-') {
        aChar = in[off++];   
        if(aChar == 'u') {
          int value=0;
          for (int i=0; i<4; i++) {
            aChar = in[off++];  
            switch (aChar) {
            case '0': case '1': case '2': case '3': case '4':
            case '5': case '6': case '7': case '8': case '9':
              value = (value << 4) + aChar - '0';
              break;
            case 'a': case 'b': case 'c':
            case 'd': case 'e': case 'f':
              value = (value << 4) + 10 + aChar - 'a';
              break;
            case 'A': case 'B': case 'C':
            case 'D': case 'E': case 'F':
              value = (value << 4) + 10 + aChar - 'A';
              break;
            default:
              throw new IllegalArgumentException("Malformed \\uxxxx encoding.");
            }
          }
          out[outLen++] = (char)value;
        } 
      } else {
        out[outLen++] = aChar;
      }
    }
    return new String (out, 0, outLen);
  }

  public static synchronized String encode(String theString) {
    int len = theString.length();
    int bufLen = len * 2;
    if (bufLen < 0) {
      bufLen = Integer.MAX_VALUE;
    }
    StringBuffer outBuffer = new StringBuffer(bufLen);

    for(int x=0; x<len; x++) {
      char aChar = theString.charAt(x);
      if ((aChar > 61) && (aChar < 127)) {       
        outBuffer.append(aChar);
        continue;
      }
      
      if ((aChar < 0x0020) || (aChar > 0x007e)) {
          outBuffer.append('-');
          outBuffer.append('u');
          outBuffer.append(toHex((aChar >> 12) & 0xF));
          outBuffer.append(toHex((aChar >>  8) & 0xF));
          outBuffer.append(toHex((aChar >>  4) & 0xF));
          outBuffer.append(toHex( aChar        & 0xF));
        } else {
          outBuffer.append(aChar);
        }
    }
    return outBuffer.toString();
  }
  
  private static char toHex(int nibble) { return hexDigit[(nibble & 0xF)]; }

  private static final char[] hexDigit = {
      '0','1','2','3','4','5','6','7','8','9','A','B','C','D','E','F'
  };  
}
