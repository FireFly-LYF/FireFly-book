package main

import (
	"flag"
	"log"
	"net"

	handler "gateway/downstream/handler_grpc"

	pb "gateway/downstream/handler_grpc/pb"

	"google.golang.org/grpc"
	"google.golang.org/grpc/reflection"
)

func main() {
	port := flag.String("port", "50052", "listen port")
	flag.Parse()

	handler.Instance = *port

	lis, err := net.Listen("tcp", ":"+*port)
	if err != nil {
		log.Fatalf("listen: %v", err)
	}

	//注册后，服务对外暴露：/gateway.UserService/Greet
	s := grpc.NewServer()
	pb.RegisterUserServiceServer(s, &handler.Greeter{})

	//让 grpcurl、BloomRPC 等工具自动发现服务和方法，不再需要 -proto 参数
	reflection.Register(s)

	log.Printf("grpc downstream listening on :%s (instance=%s)", *port, *port)
	//阻塞，一直处理 gRPC 请求
	if err := s.Serve(lis); err != nil {
		log.Fatalf("serve: %v", err)
	}
}
