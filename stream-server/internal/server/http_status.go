package server

import (
	"encoding/json"
	"net/http"
	"sync"
)

var status = struct {
	sync.Mutex
	Connections map[string]int64 `json:"connections"`
}{
	Connections: map[string]int64{},
}

func AddConnection(addr string) {
	status.Lock()
	status.Connections[addr] = 0
	status.Unlock()
}

func AddBytes(addr string, n int) {
	status.Lock()
	status.Connections[addr] += int64(n)
	status.Unlock()
}

func RemoveConnection(addr string) {
	status.Lock()
	delete(status.Connections, addr)
	status.Unlock()
}

func StartStatusServer() {
	http.HandleFunc("/status", func(w http.ResponseWriter, r *http.Request) {
		status.Lock()
		defer status.Unlock()
		b, _ := json.MarshalIndent(status, "", "  ")
		w.Header().Set("Content-Type", "application/json")
		w.Write(b)
	})
	go http.ListenAndServe(":8080", nil)
}
