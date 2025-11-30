package mux

import (
	"fmt"

	"stream-server/pkg/protocol"
)

type FLVMuxer struct {
	// you can keep state here: headers, timestamps, etc.
	wroteHeader bool
}

func NewFLVMuxer() *FLVMuxer {
	return &FLVMuxer{}
}

// WriteRaw is a temporary method: it just accepts raw FLV.
// Later, you can replace this with higher-level WriteAudio/WriteVideo.
func (m *FLVMuxer) WriteRaw(data []byte) error {
	// TODO: parse & validate FLV (use protocol package)
	_ = protocol.FLVTag{} // reference package so it's used
	if !m.wroteHeader {
		// in real impl, check & write header
		m.wroteHeader = true
	}
	// For now we do nothing with data.
	// You will hook this into downstream outputs later.
	fmt.Printf("FLVMuxer received %d bytes\n", len(data))
	return nil
}
