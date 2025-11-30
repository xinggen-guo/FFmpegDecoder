package protocol

// Very small placeholder definitions.
// Extend these according to FLV spec when needed.

type FLVTagType uint8

const (
	TagAudio FLVTagType = 8
	TagVideo FLVTagType = 9
	TagMeta  FLVTagType = 18
)

type FLVTag struct {
	Type      FLVTagType
	Timestamp uint32
	Data      []byte
}
