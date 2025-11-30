package server

import (
	"log"
	"net/http"
	"sync"
)

const (
	// how much from the beginning of the stream to cache for new clients
	previewCacheLimit = 2 * 1024 * 1024 // 2 MB
)

type LiveHub struct {
	mu    sync.RWMutex
	subs  map[chan []byte]struct{}
	cache []byte // beginning of current FLV stream
	live  bool   // whether we’re currently receiving a stream
}

func NewLiveHub() *LiveHub {
	return &LiveHub{
		subs: make(map[chan []byte]struct{}),
	}
}

// Called from HandleStream for every chunk from the Android TCP connection.
func (h *LiveHub) Broadcast(data []byte) {
	h.mu.Lock()
	defer h.mu.Unlock()

	if !h.live {
		// first data of a new session -> reset and start caching
		h.cache = h.cache[:0]
		h.live = true
		log.Printf("[LiveHub] New live session, cache reset")
	}

	// grow cache with new data, but keep it bounded
	if len(h.cache) < previewCacheLimit {
		toAppend := data
		if len(h.cache)+len(toAppend) > previewCacheLimit {
			toAppend = toAppend[:previewCacheLimit-len(h.cache)]
		}
		h.cache = append(h.cache, toAppend...)
	}

	for ch := range h.subs {
		// non-blocking send; if client is too slow, we drop its data and
		// it will naturally disconnect
		select {
		case ch <- data:
		default:
		}
	}
}

// Called when the publisher connection ends (HandleStream returns).
func (h *LiveHub) EndSession() {
	h.mu.Lock()
	defer h.mu.Unlock()

	h.live = false
	// keep cache as last session’s beginning; it will be replaced on next stream
	log.Printf("[LiveHub] Live session ended")
}

// HTTP handler: /live.flv
func (h *LiveHub) LiveHandler(w http.ResponseWriter, r *http.Request) {
	log.Printf("[LiveHub] New HTTP preview client from %s", r.RemoteAddr)

	// set FLV content type
	w.Header().Set("Content-Type", "video/x-flv")
	// keep-alive, streaming
	w.Header().Set("Cache-Control", "no-store")
	w.WriteHeader(http.StatusOK)

	// we want flush after each write
	flusher, ok := w.(http.Flusher)
	if !ok {
		http.Error(w, "Streaming unsupported", http.StatusInternalServerError)
		return
	}

	// register subscriber
	ch := make(chan []byte, 128)

	h.mu.Lock()
	h.subs[ch] = struct{}{}
	// take a copy of current cache so we can release the lock quickly
	cached := make([]byte, len(h.cache))
	copy(cached, h.cache)
	h.mu.Unlock()

	defer func() {
		h.mu.Lock()
		delete(h.subs, ch)
		h.mu.Unlock()
		close(ch)
		log.Printf("[LiveHub] HTTP preview client %s disconnected", r.RemoteAddr)
	}()

	// 1) send cached beginning of stream (header + first tags)
	if len(cached) > 0 {
		if _, err := w.Write(cached); err != nil {
			log.Printf("[LiveHub] write cache error: %v", err)
			return
		}
		flusher.Flush()
	}

	// 2) stream live data
	for {
		buf, ok := <-ch
		if !ok {
			return
		}
		if len(buf) == 0 {
			continue
		}
		if _, err := w.Write(buf); err != nil {
			// client disconnected
			return
		}
		flusher.Flush()
	}
}
