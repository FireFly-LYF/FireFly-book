// TCP 回显服务，用于测试 tcp 透明代理。
// 启动：go run ./cmd/downstream_tcp
package main

import (
	"flag"
	"io"
	"log"
	"net"
)

func main() {
	port := flag.String("port", "9010", "listen port")
	flag.Parse()

	addr := ":" + *port
	ln, err := net.Listen("tcp", addr)
	if err != nil {
		log.Fatal(err)
	}
	log.Printf("downstream tcp listening on %s", addr)
	for {
		conn, err := ln.Accept()
		if err != nil {
			log.Println("accept:", err)
			continue
		}
		go handle(conn)
	}
}

func handle(conn net.Conn) {
	defer conn.Close()
	_, _ = io.Copy(conn, conn)
}
