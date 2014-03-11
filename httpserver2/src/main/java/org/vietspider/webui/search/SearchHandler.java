/***************************************************************************
 * Copyright 2003-2007 by VietSpider - All rights reserved.                *    
 **************************************************************************/
package org.vietspider.webui.search;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.util.zip.GZIPOutputStream;

import org.apache.http.Header;
import org.apache.http.HeaderElement;
import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpRequestHandler;
import org.vietspider.common.Application;
import org.vietspider.common.io.LogService;
import org.vietspider.db.SystemProperties;
import org.vietspider.httpserver.GZipEntity;
import org.vietspider.index.SearchQuery;
import org.vietspider.server.handler.cms.DataNotFound;

/**
 *  Author : Nhu Dinh Thuan
 *          Email:nhudinhthuan@yahoo.com
 * Jun 26, 2007
 */
public abstract class SearchHandler<T> implements HttpRequestHandler  {
  
  private static final String ACCEPT_ENCODING = "Accept-Encoding";
  
  private static final String GZIP_CODEC = "gzip";
  
  protected String name;
  
  protected String type;
  
  protected String disableBot = null;
//  protected boolean logAgent = false;
//  protected String userAgent;
  
  public SearchHandler(String type) {
    this.type = type;
    
    disableBot = SystemProperties.getInstance().getValue("web.disable.bot");
    if(disableBot != null && disableBot.trim().isEmpty()) disableBot = null;
    
//    logAgent = "true".equals(SystemProperties.getInstance().getValue("web.log.agent"));
  }
  
  public String getPath() { return "/"+type + "/"+name; }

  public void handle(final HttpRequest request,
                     final HttpResponse response,
                     final HttpContext context) throws HttpException, IOException {
    String contentType = "text/html";
    try {
      if(disableBot != null) {
        String userAgent = getUserAgent(request);
        if(userAgent.toLowerCase().indexOf(disableBot) > -1) return;
      }
      
      String [] params = new String[0]; 
      
      String path  = request.getRequestLine().getUri();
      if(name != null && !name.trim().isEmpty()) {
        int idx = path.indexOf(name);
        if(idx > 0) {
          String component = path.substring(idx+name.length());
          component = component.trim();
          if(component.charAt(0) == '/') component = component.substring(1);
//          component = URLDecoder.decode(component, Application.CHARSET);
          params = component.split("/");
          for(int i = 0; i < params.length - 1; i++) {
            params[i] = URLDecoder.decode(params[i], Application.CHARSET);
          }
          if(params.length > 0) {
            String param = params[params.length - 1]; 
            if(param.indexOf('?') < 0) {
              params[params.length - 1] = URLDecoder.decode(param, Application.CHARSET);
            }
          }
        }
      }
      
      contentType = handle(request, response, context, params);
    } catch (DataNotFound exp) {
      ByteArrayOutputStream arrayOutput = new ByteArrayOutputStream();
      arrayOutput.write(exp.getMessage().getBytes());
      ByteArrayEntity byteArrayEntity = new ByteArrayEntity(arrayOutput.toByteArray());
      byteArrayEntity.setContentType(contentType);
      response.setEntity(byteArrayEntity);
    } catch (Throwable exp) {
      ByteArrayOutputStream arrayOutput = new ByteArrayOutputStream();
      StackTraceElement[] traces = exp.getStackTrace();
      try {
        arrayOutput.write(exp.toString().getBytes());
        arrayOutput.write("\n".getBytes());
      }catch (Exception e) {
        LogService.getInstance().setThrowable(e);
      }     

      for(StackTraceElement ele : traces){
        try {
          arrayOutput.write(ele.toString().getBytes());
          arrayOutput.write("\n".getBytes());
        }catch (Exception e) {
          LogService.getInstance().setThrowable(e);
        }
      }

      setOutputData(request, response, contentType, arrayOutput.toByteArray());
    }
  }
  
  abstract public String handle(final HttpRequest request, 
                       final HttpResponse response,
                       final HttpContext context, String...params) throws Exception ;
  
  @SuppressWarnings("unused")
  protected String write(final HttpRequest request, 
                       final HttpResponse response,
                       final HttpContext context, T bean, SearchQuery query) throws Exception {
    ByteArrayOutputStream arrayOutput = new ByteArrayOutputStream();
    String contentType = "text/html";
    try {
      contentType = render(arrayOutput, bean, getCookie(request), query);
    } catch (Exception e) {
      arrayOutput.write(e.toString().getBytes());
    }
    setOutputData(request, response, contentType, arrayOutput.toByteArray());
    return contentType;
  }
  
  @SuppressWarnings("unused")
  protected String redirect(final HttpRequest request, 
                       final HttpResponse response,
                       final HttpContext context, String text, SearchQuery query) throws Exception {
    String contentType = "text/html";
    byte [] bytes = text.getBytes(Application.CHARSET);
    setOutputData(request, response, contentType, bytes);
    return contentType;
  }
  
  @SuppressWarnings("unused")
  protected String redirectURL(final HttpRequest request, 
                       final HttpResponse response,
                       final HttpContext context, String url, SearchQuery query) throws Exception {
    StringBuilder builder = new StringBuilder();
    builder.append("<html><head><title>Redirecting...</title>");
    builder.append("<meta http-equiv=\"refresh\" content=\"0;url=").append(url).append("\">");
    builder.append("</head><body>");
    builder.append("Attempting to redirect to <a href=\"").append(url).append("\">");
    builder.append(url).append("</a></body></html>");
    String contentType = "text/html";
    byte [] bytes = builder.toString().getBytes(Application.CHARSET);
    setOutputData(request, response, contentType, bytes);
    return contentType;
  }
  
  protected void setOutputData(final HttpRequest request,
      final HttpResponse response, String contentType, byte[] bytes) {
    if(isZipResponse(request)) {
      GZipEntity byteArrayEntity = new GZipEntity(gzipCompress(bytes));
      byteArrayEntity.setContentType(contentType);
      response.setEntity(byteArrayEntity);  
    } else {
      ByteArrayEntity byteArrayEntity = new ByteArrayEntity(bytes);
      byteArrayEntity.setContentType(contentType);
      response.setEntity(byteArrayEntity);  
    }
  }
  
  protected boolean isZipResponse(HttpRequest request) {
    Header encHeader = request.getFirstHeader(ACCEPT_ENCODING);
    if (encHeader == null) return false;
    HeaderElement[] codecs = encHeader.getElements();

    for (int i = 0; i < codecs.length; i++) {
      if(codecs[i].getName().equalsIgnoreCase(GZIP_CODEC)) return true;
    }
    return false;
  }
  
  private byte [] gzipCompress(byte [] bytes) {
    ByteArrayOutputStream byteArrayStream = new ByteArrayOutputStream();
    try {
      GZIPOutputStream gzip = new GZIPOutputStream(byteArrayStream);
      gzip.write(bytes, 0, bytes.length);
      gzip.close();
    } catch (Exception e) {
      LogService.getInstance().setThrowable("SERVER", e);
    }
    return byteArrayStream.toByteArray();
  }
  
  protected String[] getCookie(HttpRequest request) {
    Header header = request.getFirstHeader("Cookie");
//    System.out.println(" trong khi get first cookie "+ header);
    if(header == null) return null;
    String value  = header.getValue();
//    System.out.println("trong cms hanler "+ value);
    if(value == null || (value = value.trim()).isEmpty()) return null;
    return value.split("#");
  }
  
  protected String getUserAgent(HttpRequest request) {
    Header header = request.getFirstHeader("User-Agent");
//    System.out.println(" trong khi get first cookie "+ header);
    if(header == null) return "";
    String value  = header.getValue();
//    System.out.println("trong cms hanler "+ value);
    if(value == null) return "";
    return value;
  }
  
  public abstract String render(OutputStream output, T t, String [] cookies, SearchQuery query) throws Exception ;
  
  public void redirect(final HttpRequest request, final HttpResponse response) throws Exception {
    StringBuilder builder = new StringBuilder();
    builder.append("<html>");
    builder.append("<head>");
    builder.append("<meta http-equiv=\"Refresh\" content=\"2;URL=/\">");
    builder.append("</head>");
    builder.append("<body> Invalid access or not found data or data was deleted</body>");
    builder.append("</html>");
    
    byte [] bytes  = builder.toString().getBytes(Application.CHARSET);
    setOutputData(request, response, "text/html", bytes);
  }
  
  
  protected String crawlPage() {
    StringBuilder builder = new StringBuilder();
    builder.append("<html><head><title>Error...</title>");
    builder.append("<meta http-equiv=\"Content-Type\" content=\"text/html; charset=utf-8\"/>");
    builder.append("</head><body>");
    builder.append("<h1>Bạn hiện đang truy cập quá nhanh.");
    builder.append(" Hãy đợi 5 giây sau rồi nhấn Refesh hoặc phím F5 để thử lại.</h1>");
    builder.append("</body></html>");
    return builder.toString();
  }
  
}
