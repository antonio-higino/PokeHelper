// Package testutil provides a real gRPC transport over an in-memory listener.
package testutil

import (
	"context"
	"net"
	"testing"
	"time"

	transport "github.com/antonio-higino/pokehelper/rpc/server"
	"google.golang.org/grpc"
	"google.golang.org/grpc/credentials/insecure"
	healthpb "google.golang.org/grpc/health/grpc_health_v1"
	reflectionpb "google.golang.org/grpc/reflection/grpc_reflection_v1"
	"google.golang.org/grpc/test/bufconn"
)

func Dial(t testing.TB, service string, register func(*grpc.Server)) *grpc.ClientConn {
	t.Helper()
	listener := bufconn.Listen(1 << 20)
	server, _ := transport.New(register)
	go func() { _ = server.Serve(listener) }()
	conn, err := grpc.NewClient("passthrough:///bufconn", grpc.WithContextDialer(func(ctx context.Context, _ string) (net.Conn, error) { return listener.DialContext(ctx) }), grpc.WithTransportCredentials(insecure.NewCredentials()))
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() { conn.Close(); server.Stop(); listener.Close() })
	ctx, cancel := context.WithTimeout(context.Background(), time.Second)
	defer cancel()
	for _, name := range []string{"", service} {
		response, err := healthpb.NewHealthClient(conn).Check(ctx, &healthpb.HealthCheckRequest{Service: name})
		if err != nil || response.GetStatus() != healthpb.HealthCheckResponse_SERVING {
			t.Fatalf("health %q: %v %v", name, response, err)
		}
	}
	stream, err := reflectionpb.NewServerReflectionClient(conn).ServerReflectionInfo(ctx)
	if err != nil {
		t.Fatal(err)
	}
	if err = stream.Send(&reflectionpb.ServerReflectionRequest{MessageRequest: &reflectionpb.ServerReflectionRequest_ListServices{ListServices: ""}}); err != nil {
		t.Fatal(err)
	}
	response, err := stream.Recv()
	if err != nil {
		t.Fatal(err)
	}
	found := false
	for _, item := range response.GetListServicesResponse().GetService() {
		if item.GetName() == service {
			found = true
		}
	}
	if !found {
		t.Fatalf("reflection did not list %s: %v", service, response)
	}
	_ = stream.CloseSend()
	return conn
}
