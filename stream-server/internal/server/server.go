package server

import (
	"errors"
	"fmt"
	"io"
	"log"
	"net"
	"net/http"
	"stream-server/internal/config"
	"stream-server/internal/mux"
	"stream-server/internal/server/flvfilter"
	"stream-server/internal/storage"
)

type TCPServer struct {
	cfg      *config.Config
	listener net.Listener
	hub      *LiveHub
}

func NewTCPServer(cfg *config.Config) *TCPServer {
	return &TCPServer{
		cfg: cfg,
		hub: NewLiveHub(),
	}
}

func (s *TCPServer) Start() error {
	// ---------- HTTP server ----------
	go func() {
		m := http.NewServeMux()

		// IMPORTANT: /live.flv is registered here
		m.HandleFunc("/live.flv", s.hub.LiveHandler)

		// static files from ./web
		m.Handle("/", http.FileServer(http.Dir("web")))

		addr := ":8080"
		log.Println("HTTP server listening on", addr)
		if err := http.ListenAndServe(addr, m); err != nil {
			log.Printf("HTTP server error: %v\n", err)
		}
	}()

	// ---------- TCP ingest ----------
	addr := fmt.Sprintf("%s:%d", s.cfg.Server.Host, s.cfg.Server.Port)
	ln, err := net.Listen("tcp", addr)
	if err != nil {
		return err
	}
	s.listener = ln
	log.Printf("TCP ingest listening on %s\n", addr)

	for {
		conn, err := ln.Accept()
		if err != nil {
			log.Printf("accept error: %v\n", err)
			continue
		}
		go s.handleConn(conn)
	}
}
func (s *TCPServer) handleConn(conn net.Conn) {
	defer conn.Close()
	remote := conn.RemoteAddr().String()
	log.Printf("New connection from %s\n", remote)

	recFile, err := storage.NewFileSink("stream_" + sanitizeFilename(remote) + ".flv")
	if err != nil {
		log.Printf("create file sink error: %v\n", err)
		return
	}
	defer recFile.Close()

	filter := flvfilter.New(func(chunk []byte) error {
		// write to disk
		if err := recFile.Write(chunk); err != nil {
			return err
		}
		// broadcast to all HTTP clients
		s.hub.Broadcast(chunk)
		return nil
	})
	defer filter.Close()

	buf := make([]byte, 4096)
	for {
		n, err := conn.Read(buf)
		if n > 0 {
			if err2 := filter.Feed(buf[:n]); err2 != nil && !errors.Is(err2, flvfilter.ErrClosed) {
				log.Printf("flv filter error from %s: %v", remote, err2)
				break
			}
		}
		if err != nil {
			if err != io.EOF {
				log.Printf("conn read error from %s: %v", remote, err)
			}
			break
		}
	}

	s.hub.EndSession()
}

func isTimeout(err error) bool {
	if ne, ok := err.(net.Error); ok && ne.Timeout() {
		return true
	}
	return false
}

func sanitizeFilename(remote string) string {
	b := []byte(remote)
	for i := range b {
		if b[i] == ':' {
			b[i] = '_'
		}
	}
	return string(b)
}

// sink is a callback that receives raw FLV bytes
func HandleStream(r io.Reader, muxer *mux.FLVMuxer, sink func([]byte) error) error {
	buf := make([]byte, 188*10)

	for {
		n, err := r.Read(buf)
		if n > 0 {
			data := make([]byte, n)
			copy(data, buf[:n])

			// let muxer inspect/validate (optional now)
			if err2 := muxer.WriteRaw(data); err2 != nil {
				return err2
			}

			// write to recording + hub
			if err2 := sink(data); err2 != nil {
				return err2
			}
		}
		if err != nil {
			if err == io.EOF {
				return nil
			}
			return err
		}
	}
}
