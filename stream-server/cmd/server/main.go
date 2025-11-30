package main

import (
	"flag"
	"log"

	"stream-server/internal/config"
	"stream-server/internal/server"
)

func main() {
	cfgPath := flag.String("config", "configs/prod.yaml", "config file")
	flag.Parse()

	cfg := config.Load(*cfgPath) // one return value

	s := server.NewTCPServer(cfg)

	if err := s.Start(); err != nil {
		log.Fatalf("server error: %v", err)
	}
}
