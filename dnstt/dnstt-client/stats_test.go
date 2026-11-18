package main

import (
	"strings"
	"testing"
)

func TestStatsDeltaAndFormat(t *testing.T) {
	prev := statsSnapshot{queriesData: 10, queriesPoll: 5, bytesUp: 1024, kcpRetrans: 1}
	cur := statsSnapshot{queriesData: 40, queriesPoll: 9, bytesUp: 1024 + 15*2048, bytesDown: 15 * 1024,
		replies: 30, repliesData: 20, repliesErr: 2, kcpOut: 50, kcpRetrans: 6, kcpLost: 2, dropOut: 3}
	d := cur.sub(prev)
	if d.queriesData != 30 || d.queriesPoll != 4 || d.kcpRetrans != 5 || d.dropOut != 3 {
		t.Fatalf("bad delta %+v", d)
	}
	if !d.busy() {
		t.Fatal("expected busy")
	}
	line := formatStats(d, 15, 7, 1024, 350, 900)
	for _, want := range []string{"stats 15s:", "up 2.0 KB/s", "down 1.0 KB/s", "queries data 30 poll 4",
		"replies 30 data 20 err 2 tc 0", "kcp out 50 retrans 5 lost 2", "queue 7/1024 drop out 3 in 0", "srtt 350ms rto 900ms"} {
		if !strings.Contains(line, want) {
			t.Errorf("%q missing %q", line, want)
		}
	}
}

func TestStatsIdleIsQuiet(t *testing.T) {
	prev := statsSnapshot{queriesPoll: 3}
	cur := statsSnapshot{queriesPoll: 8, replies: 5}
	if cur.sub(prev).busy() {
		t.Fatal("polling alone should not be logged")
	}
}
