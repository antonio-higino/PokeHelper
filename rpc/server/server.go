// Package server owns transport lifecycle shared by the Go services.
package server

import (
	"context"
	"errors"
	"net"
	"net/http"
	"time"

	"google.golang.org/grpc"
	"google.golang.org/grpc/health"
	healthpb "google.golang.org/grpc/health/grpc_health_v1"
	"google.golang.org/grpc/reflection"
)

func New(register func(*grpc.Server)) (*grpc.Server, *health.Server) {
	s := grpc.NewServer()
	register(s)
	h := health.NewServer()
	healthpb.RegisterHealthServer(s, h)
	h.SetServingStatus("", healthpb.HealthCheckResponse_SERVING)
	for name := range s.GetServiceInfo() {
		h.SetServingStatus(name, healthpb.HealthCheckResponse_SERVING)
	}
	reflection.Register(s)
	return s, h
}

// Run retains the legacy HTTP endpoints and drains both transports on shutdown.
// Bound draining so an uncooperative client cannot block SIGTERM indefinitely.
func Run(ctx context.Context, s *grpc.Server, h *health.Server, grpcAddress, httpAddress string, handler http.Handler) error {
	listener, err := net.Listen("tcp", grpcAddress)
	if err != nil {
		return err
	}
	httpServer := &http.Server{Addr: httpAddress, Handler: handler, ReadHeaderTimeout: 5 * time.Second}
	failures := make(chan error, 2)
	go func() { failures <- s.Serve(listener) }()
	go func() { failures <- httpServer.ListenAndServe() }()
	select {
	case <-ctx.Done():
	case err = <-failures:
	}
	h.Shutdown()
	drained := make(chan struct{})
	go func() { s.GracefulStop(); close(drained) }()
	shutdownCtx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()
	_ = httpServer.Shutdown(shutdownCtx)
	_ = httpServer.Close()
	select {
	case <-drained:
	case <-shutdownCtx.Done():
		s.Stop()
		<-drained
	}
	if errors.Is(err, http.ErrServerClosed) || errors.Is(err, grpc.ErrServerStopped) {
		return nil
	}
	return err
}
