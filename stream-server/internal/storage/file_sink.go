package storage

import (
	"io"
	"os"
)

type FileSink struct {
	f io.WriteCloser
}

func NewFileSink(path string) (*FileSink, error) {
	f, err := os.Create(path)
	if err != nil {
		return nil, err
	}
	return &FileSink{f: f}, nil
}

func (s *FileSink) Write(b []byte) error {
	if s == nil || s.f == nil {
		return nil
	}
	_, err := s.f.Write(b)
	return err
}

func (s *FileSink) Close() error {
	if s == nil || s.f == nil {
		return nil
	}
	return s.f.Close()
}

// --- new ---

// MultiSink writes to many io.Writer targets.
type MultiSink struct {
	sinks []io.Writer
}

func NewMultiSink(writers ...io.Writer) *MultiSink {
	return &MultiSink{sinks: writers}
}

func (m *MultiSink) Write(b []byte) error {
	for _, w := range m.sinks {
		if w == nil {
			continue
		}
		if _, err := w.Write(b); err != nil {
			return err
		}
	}
	return nil
}
