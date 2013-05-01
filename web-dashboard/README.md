Web-Dashboard
============

The example web-dashboard application depends on a running instance of sdncon and a web server to serve content. The web-dashboard files are divided into two parts:

    document-root: Static HTML, javascript, and css files.
    cgi-bin-root : Python based CGI scripts.

To run the web-dashboard, copy the files into the an apropriate webserver directory for the content type.

On Ubuntu 12.04 with a default Apache2 install:

1. Move `document-root` contents into /var/www
2. Move `cgi-bin-root` contents into /usr/lib/cgi-bin

If the webserver does not use `/cgi-bin` for CGI file access, modify the `CGI_PATH` variable in the dashboard.html file to the apropriate path.

