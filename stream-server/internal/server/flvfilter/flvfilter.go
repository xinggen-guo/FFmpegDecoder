package flvfilter

import (
	"encoding/binary"
	"errors"
	"fmt"
)

const (
	tagTypeAudio  = 8
	tagTypeVideo  = 9
	tagTypeScript = 18
)

type OnChunk func([]byte) error

type Filter struct {
	buf        []byte
	headerSent bool
	onChunk    OnChunk
}

func New(onChunk OnChunk) *Filter {
	return &Filter{onChunk: onChunk}
}

func (f *Filter) Feed(data []byte) error {
	if len(data) == 0 {
		return nil
	}
	f.buf = append(f.buf, data...)

	// 1) Send FLV header + first PrevTagSize once
	if !f.headerSent {
		if len(f.buf) < 13 {
			return nil
		}
		if string(f.buf[:3]) != "FLV" {
			return fmt.Errorf("not FLV stream (magic=%q)", f.buf[:3])
		}

		headerSize := int(binary.BigEndian.Uint32(f.buf[5:9]))
		if len(f.buf) < headerSize+4 {
			return nil
		}

		headerAndPrev := f.buf[:headerSize+4]
		f.buf = f.buf[headerSize+4:]

		if err := f.onChunk(headerAndPrev); err != nil {
			return err
		}
		f.headerSent = true
	}

	// 2) Parse and forward *all* full tags
	for {
		if len(f.buf) < 15 { // 11-byte tag header + 4-byte PrevTagSize
			return nil
		}

		tagType := f.buf[0]
		dataSize := int(f.buf[1])<<16 | int(f.buf[2])<<8 | int(f.buf[3])
		tagTotal := 11 + dataSize + 4
		if len(f.buf) < tagTotal {
			return nil
		}

		tag := f.buf[:tagTotal]
		f.buf = f.buf[tagTotal:]

		// Only forward real media / metadata tags
		if tagType == tagTypeAudio || tagType == tagTypeVideo || tagType == tagTypeScript {
			if err := f.onChunk(tag); err != nil {
				return err
			}
		}
	}
}

var ErrClosed = errors.New("filter closed")

func (f *Filter) Close() error {
	f.buf = nil
	return ErrClosed
}
