package main

import (
	"flag"
	"log"

	handler "gateway/downstream/handler_http"

	"github.com/gin-gonic/gin"
)

func main() {
	port := flag.String("port", "9002", "listen port")
	workMS := flag.Int("work-ms", 5, "simulated business latency in ms for /user and /order (0=off); /health stays cheap")
	flag.Parse()

	handler.Instance = *port
	handler.WorkMS = *workMS

	r := gin.New()
	r.Use(gin.Recovery())
	// release 下默认不挂 Logger，避免访问日志把压测打满；需要调试可改回 gin.Default()
	if gin.Mode() != gin.ReleaseMode {
		r.Use(gin.Logger())
	}

	r.GET("/user", handler.User)
	r.GET("/order", handler.Order)
	r.GET("/health", handler.Health)

	addr := ":" + *port
	log.Printf("downstream listening on %s (instance=%s, work-ms=%d)", addr, *port, *workMS)
	if err := r.Run(addr); err != nil {
		log.Fatal(err)
	}
}
