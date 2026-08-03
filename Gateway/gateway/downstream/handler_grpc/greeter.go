package handler_grpc

import (
	"context"
	"fmt"

	pb "gateway/downstream/handler_grpc/pb"
)

// Instance 由 main 启动时设置，类似 HTTP downstream 的 handler.Instance
var Instance = "50052"

type Greeter struct {
	pb.UnimplementedUserServiceServer
}

func (g *Greeter) Greet(ctx context.Context, req *pb.HelloRequest) (*pb.HelloReply, error) {
	name := req.GetName()
	if name == "" {
		name = "world"
	}
	return &pb.HelloReply{
		Message:  fmt.Sprintf("Hello %s", name),
		Instance: Instance,
	}, nil
}
