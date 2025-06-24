# TFTP-UDP-Server

## Overview

A server-side implementation of the Trivial File Transfer Protocol (TFTP) using the User Datagram Protocol (UDP). Implemented in java version 17 and primarily makes use of the java.net and java.io packages. This implementation follows the specification outlined in [RFC 1350](https://www.rfc-editor.org/rfc/pdfrfc/rfc1350.txt.pdf), but a slightly simplified version: support for octet mode only, and support only for error handling when the requested file cannot be found.

## How To Run

```bash
# 1. Clone repo
git clone https://github.com/jude-james/TFTP-UDP-Server.git

# 2. Change directory
cd "TFTP-UDP-Server"

# 3. Run 
java src/main/java/Server.java
```
