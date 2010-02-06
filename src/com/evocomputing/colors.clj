;; by Joel Boehland http://github.com/jolby/colors
;; February 4, 2010

;; Copyright (c) Joel Boehland, 2010. All rights reserved.  The use
;; and distribution terms for this software are covered by the Eclipse
;; Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;; which can be found in the file epl-v10.html at the root of this
;; distribution.  By using this software in any fashion, you are
;; agreeing to be bound by the terms of this license.  You must not
;; remove this notice, or any other, from this software.

(ns
    #^{:doc
       "Color manipulation routines. This is mostly a port of the
color.rb module in the ruby SASS project to Clojure:
http://github.com/nex3/haml/blob/master/lib/sass/script/color.rb

Further references:
HSL and HSV: http://en.wikipedia.org/wiki/Luminance-Hue-Saturation
RGB color space: http://en.wikipedia.org/wiki/RGB_color_space
http://en.wikipedia.org/wiki/Hue#Computing_hue_from_RGB
http://www.w3.org/TR/css3-color/#hsl-color
"
       :author "Joel Boehland"}

  com.evocomputing.colors
  (import (java.awt Color))
  (:use (clojure.contrib core except math)))

(declare html4-colors-name-to-rgbnum html4-colors-name-to-rgb
         html4-colors-rgbnum-to-name html4-colors-rgb-to-name
         rgb-int-to-components rgba-int-to-components rgba-int-from-components
         rgb-to-hsl hsl-to-rgb)

(defstruct
    #^{:doc
       "Structure representing a color. Default representation
    is an array of integers mapping to the respective RGB(A)
    values. This structure also supports holding an array of float
    values mapping to the respective HSL values as well"}
  color
  ;;4 integer array representation of the rgba values. Rgba values
  ;;must be between 0 and 255 inclusive
  :rgba
  ;;3 float array holding the HSL values for this color. The
  ;;saturation and lightness must be between 0.0 and 100.0. Hue must
  ;;be between 0.0 and 360.0
  :hsl)

(def allowable-rgb-keys
     #{:r :red :g :green :b :blue})

(def allowable-hsl-keys
     #{:h :hue :s :saturation :l :lightness})

(defn create-color-dispatch
  ""
  ([args]
  (cond
   (or (symbol? args) (string? args) (keyword? args)) ::symbolic-color
   (integer? args) ::rgb-int
   (and (map? args) (some allowable-rgb-keys (keys args))) ::rgb-map
   (and (map? args) (some allowable-hsl-keys (keys args))) ::hsl-map
   (and (or (seq? args) (seqable? args)) (#{3 4} (count args))) ::rgb-array
   (= (class args) Color) Color
   true (throw (IllegalArgumentException.
                (format "Don't know how to process args: %s" args)))))
  ([arg & others]
     (let [args (conj others arg)]
       (cond
        (and (keyword? arg) (some allowable-rgb-keys args)) ::rgb-map
        (and (keyword? arg) (some allowable-hsl-keys args)) ::hsl-map
        (and (or (seq? args) (seqable? args)) (#{3 4} (count args))) ::rgb-array
        true (throw (IllegalArgumentException.
                     (format "Don't know how to process args: %s" arg)))))))

(defmacro create-color-with-meta
  "Create color with type meta"
  [& body]
  `(with-meta
     ~@body
      {:type ::color}))

(defn rgb-int?
  "If the passed in value is an integer in the range 0 - 255
  inclusive, return true, otherwise return false"
  [rgb-int]
  (and (integer? rgb-int) (and (>= rgb-int 0) (<= rgb-int 255))))

(defn clamp-rgb-int
  "Clamp the integer value to be within the range 0 - 255"
  [rgb-int]
  (max (min rgb-int 255) 0))

(defn unit-float?
  "Return true if passed in float is in the range 0.0 - 1.0. False otherwise"
  [fval]
  (and (>= fval 0.0) (<= fval 1.0)))

(defn percent-float?
  "Return true if passed in float is in the range 0.0 - 100.0. False otherwise"
  [fval]
  (and (>= fval 0.0) (<= fval 100.0)))

(defn circle-float?
  "Return true if passed in float is in the range 0.0 - 360.0. False otherwise"
  [fval]
  (and (>= fval 0.0) (<= fval 360.0)))

(defn check-convert-unit-float-to-int
  "Check that the passed in float is in the range 0.0 - 1.0, then
convert it to the appropriate integer in the range 0 - 255"
  [fval]
  (throw-if-not (unit-float? fval)
                "fval must be a float between 0.0 and 0.1: %s" fval)
  (int (+ 0.5 (* fval 255))))

(defn maybe-convert-alpha
  "If alpha is a float value, try to convert to integer in range 0 -
255, otherwise return as-is"
  [alpha]
  (if (rgb-int? alpha) alpha
      (throw-if-not (float? alpha)
                    "alpha must be an integer in range 0 - 255 or float: %s" alpha))
  (check-convert-unit-float-to-int alpha))

(defn check-rgba
  "Check that every element in the passed in rgba sequence is an
integer in the range 0 - 255"
  ([rgba]
     (throw-if-not (and (= (count rgba) 4) (every? #'rgb-int? rgba))
                   "Must contain 4 integers in range 0 - 255: %s" rgba)
     rgba)
  ([r g b a]
     (throw-if-not (every? #'rgb-int? [r g b a])
     "Must contain 4 integers in range 0 - 255: %s" [r g b a])))

(defn check-hsl
  "Check that every element is of the format:
- 1st, H (Hue): Float value in the range of: 0.0 - 360.0
- 2nd, S (Saturation): Float value in the range: 0.0 - 100.0
- 3rd, L (Lightness or Luminance): Float value in the range 0.0 - 100.0
"
  ([hsl]
     (throw-if-not (= (count hsl) 3)
                   "Must contain 3 floats representing HSL: %s" hsl)
     (check-hsl (hsl 0) (hsl 1) (hsl 2))
     hsl)
  ([h s l] (throw-if-not (and (circle-float? h)
                              (percent-float? s) (percent-float? l))
                         "Elements must be of the form:
H (Hue): Float value in the range of: 0.0 - 360.0
S (Saturation): Float value in the range: 0.0 - 100.0
L (Lightness or Luminance): Float value in the range 0.0 - 100.0
%s %s %s" h s l)))

(defmulti create-color
  "Create a color struct using the passed in args.

This will create a color struct that has RGBA integer values in the range:
- R (Red): Integer in range 0 - 255
- G (Green): Integer in range 0 - 255
- B (Blue): Integer in range 0 - 255
- A (Alpha): Integer in range 0 - 255, with default as 255 (100% opacity)

And HSL values with the range:
- H (Hue): Float value in the range of: 0.0 - 360.0
- S (Saturation): Float value in the range: 0.0 - 100.0
- L (Lightness or Luminance): Float value in the range 0.0 - 100.0

This multimethod is very liberal in what it will accept to create a
color. Following is a list of acceptable formats:

Single Arg
- Symbolic: Either a string or keyword or symbol that
matches an entry in the symbolic color pallette. Currently, this is
the html4 colors map, but there are plans to allow the symbolic color
map to be set to any custom pallette.
  examples:
  (create-color \"blue\")
  (create-color :blue)

- Hexstring: A hex string representation of an RGB(A) color
  examples:
  (create-color \"0xFFCCAA\")
  (create-color \"#FFCCAA\")
  (create-color \"Ox80FFFF00\") ;; alpha = 128

- Integer: An integer representation of an RGB(A) color
  examples:
  (create-color 0xFFCCAA) ;; integer in hexidecimal format
  (create-color 16764074) ;; same integer in decimal format

- Sequence or array of RGB(A) integers
  :examples
  (create-color [255 0 0])
  (create-color [255 0 0 128]) ;;alpha = 128

- Map of either RGB (A) kw/values or HSL(A) kw/values
  Allowable RGB keys: :r :red :g :green :b :blue
  Allowable HSL keys: :h :hue :s :saturation :l :lightness

  examples:
  (create-color {:r 255 :g 0 :blue 0})
  (create-color {:r 255 :g 0 :blue 0 :a 128})
  (create-color {:h 120.0 :s 100.0 :l 50.0})
  (create-color {:h 120.0 :s 100.0 :l 50.0 :a 128})

Multiple Arg
- Sequence or array of RGB(A) integers
  :examples
  (create-color 255 0 0)
  (create-color 255 0 0 128) ;;alpha = 128

- Assoc list of either RGB (A) kw/values or HSL(A) kw/values
  Allowable RGB keys: :r :red :g :green :b :blue
  Allowable HSL keys: :h :hue :s :saturation :l :lightness

  examples:
  (create-color :r 255 :g 0 :blue 0)
  (create-color :r 255 :g 0 :blue 0 :a 128)
  (create-color :h 120.0 :s 100.0 :l 50.0)
  (create-color :h 120.0 :s 100.0 :l 50.0 :a 128)
"

  create-color-dispatch)

(defmethod create-color ::symbolic-color [colorsym]
  (letfn [(stringify [colorsym]
             (if (or (symbol? colorsym) (keyword? colorsym))
               (.toLowerCase (name colorsym))
               colorsym))]
    (let [colorsym (stringify colorsym)]
      (if-let [rgb-int (html4-colors-name-to-rgbnum colorsym)]
        (create-color (rgb-int-to-components rgb-int))
        (create-color
         (rgba-int-to-components (Integer/decode colorsym)))))))

(defmethod create-color ::rgb-int [rgb-int]
  (create-color (rgba-int-to-components rgb-int)))

(defmethod create-color ::rgb-array [rgb-array & others]
  (let [rgb-array (if others (vec (conj others rgb-array)) rgb-array)
        ;;if alpha wasn't provided, use default of 255
        rgba (if (or (= 3 (count rgb-array)) (nil? (rgb-array 3)))
               (conj rgb-array 255)
               rgb-array)]
    (check-rgba rgba)
    (create-color-with-meta
      (struct color rgba
              (rgb-to-hsl (rgba 0) (rgba 1) (rgba 2))))))

(defmethod create-color ::rgb-map [rgb-map & others]
  (let [rgb-map (if others (apply assoc {} (vec (conj others rgb-map))) rgb-map)
        ks (keys rgb-map)
        rgb (into [] (map #(rgb-map %)
                           (map #(some % ks)
                                '(#{:r :red} #{:g :green} #{:b :blue}))))
        alpha (or (:a rgb-map) (:alpha rgb-map))
        rgba (check-rgba (if alpha (conj rgb alpha) (conj rgb 255)))]
    (create-color rgba)))

(defmethod create-color ::hsl-map [hsl-map & others]
  (let [hsl-map (if others (apply assoc {} (vec (conj others hsl-map))) hsl-map)
        ks (keys hsl-map)
        hsl (check-hsl (into [] (map #(hsl-map %)
                                     (map #(some % ks)
                                          '(#{:h :hue} #{:s :saturation} #{:l :lightness})))))
        rgb (hsl-to-rgb (hsl 0) (hsl 1) (hsl 2))
        alpha (or (:a hsl-map) (:alpha hsl-map))
        rgba (check-rgba (if alpha (conj rgb alpha) (conj rgb 255)))]
    (create-color rgba)))

(defmethod create-color Color [color]
  (create-color [(.getRed color) (.getGreen color)
                 (.getBlue color) (.getAlpha color)]))

(defn red "Return the red (int) component of this color" [color] ((:rgba color) 0))
(defn green "Return the green (int) component of this color" [color] ((:rgba color) 1))
(defn blue "Return the blue (int) component of this color" [color] ((:rgba color) 2))
(defn hue "Return the hue (float) component of this color" [color] ((:hsl color) 0))
(defn saturation "Return the saturation (float) component of this color" [color] ((:hsl color) 1))
(defn lightness "Return the lightness (float) component of this color" [color] ((:hsl color) 2))
(defn alpha "Return the alpha (int) component of this color" [color] ((:rgba color) 3))

(defn rgb-int
  "Return a integer (RGB) representation of this color"
  [color]
  (rgb-int-from-components (red color) (green color) (blue color)))

(defn rgba-int
  "Return a integer (RGBA) representation of this color"
  [color]
  (rgba-int-from-components (red color) (green color) (blue color) (alpha color)))

(defn color-name
  "If there is an entry for this color value in the symbolic color
names map, return that. Otherwise, return the hexcode string of this
color's rgba integer value"
  [color]
  (if-let [color-name (html4-colors-rgbnum-to-name (rgb-int color))]
    color-name
    (format "%#08x" (rgba-int color))))

(defmethod print-method ::color [color writer]
  (print-method (format "#<color: %s R: %d, G: %d, B: %d, H: %.2f, S: %.2f, L: %.2f, A: %d>"
                        (color-name color) (red color) (green color) (blue color)
                        (hue color) (saturation color) (lightness color) (alpha color))
                writer))

(defn rgb-int-from-components
  "Convert a vector of the 3 rgb integer values into a color given in
  numeric rgb format"
  [r g b]
  (bit-or (bit-shift-left (bit-and r 0xFF) 16)
          (bit-or (bit-shift-left (bit-and g 0xFF) 8)
                 (bit-shift-left (bit-and b 0xFF) 0))))

(defn rgba-int-from-components
  "Convert a vector of the 4 rgba integer values into a color given in
  numeric rgb format "
  [r g b a]
  (bit-or (bit-shift-left (bit-and a 0xFF) 24)
          (bit-or (bit-shift-left (bit-and r 0xFF) 16)
                  (bit-or (bit-shift-left (bit-and g 0xFF) 8)
                          (bit-shift-left (bit-and b 0xFF) 0)))))

(defn rgb-int-to-components
  "Convert a color given in numeric rgb format into a vector of the 3
  rgb integer values"
  [rgb-int]
  (into []
        (reverse (for [n (range 0 3)]
                   (bit-and (bit-shift-right rgb-int (bit-shift-left n 3)) 0xff)))))

(defn rgba-int-to-components
  "Convert a color given in numeric rgb format into a vector of the 4
  rgba integer values"
  [rgba-int]
  (conj (rgb-int-to-components rgba-int)
        (bit-shift-right rgba-int 24)))

;;Color ops
(defmacro def-color-bin-op
  "Macro for creating binary operations between two colors.

Arguments
name - the name of the operation.

bin-op - the function that takes two values, producing one from
those. (eg: + - * /). This op will be applied pairwise to the
repective color's rgba components to create a new color with the
resultant rgba components.

Result
color - a new color that is the result of the binary operation."
  [name bin-op]
  `(defn ~name
     [color1# color2#]
     (create-color (vec (map clamp-rgb-int (map ~bin-op (:rgba color1#) (:rgba color2#)))))))

(def-color-bin-op color-add +)
(def-color-bin-op color-sub -)
(def-color-bin-op color-mult *)
(def-color-bin-op color-div /)

(def html4-colors-name-to-rgbnum
     {
      "black"    0x000000
      "silver"   0xc0c0c0
      "gray"     0x808080
      "white"    0xffffff
      "maroon"   0x800000
      "red"      0xff0000
      "purple"   0x800080
      "fuchsia"  0xff00ff
      "green"    0x008000
      "lime"     0x00ff00
      "olive"    0x808000
      "yellow"   0xffff00
      "navy"     0x000080
      "blue"     0x0000ff
      "teal"     0x008080
      "aqua"     0x00ffff
      })

(def html4-colors-rgbnum-to-name
     (into {} (map (fn [[k v]] [v k]) html4-colors-name-to-rgbnum)))

(def html4-colors-name-to-rgb
     (into {} (for [[k v] html4-colors-name-to-rgbnum] [k (rgb-int-to-components v)])))

(def html4-colors-rgb-to-name
     (into {} (map (fn [[k v]] [v k]) html4-colors-name-to-rgb)))

(defn hue-to-rgb
  "Convert hue color to rgb components
Based on algorithm described in:
http://en.wikipedia.org/wiki/Hue#Computing_hue_from_RGB
and:
http://www.w3.org/TR/css3-color/#hsl-color"
  [m1, m2, hue]
  (let* [h (cond
           (< hue 0) (inc hue)
           (> hue 1) (dec hue)
           :else hue)]
        (cond
         (< (* h 6) 1) (+ m1 (* (- m2 m1) h 6))
         (< (* h 2) 1) m2
         (< (* h 3) 2) (+ m1 (* (- m2 m1) (- (/ 2.0 3) h) 6))
         :else m1)))

(defn hsl-to-rgb
  "Given color with HSL values return vector of r, g, b.

Based on algorithms described in:
http://en.wikipedia.org/wiki/Luminance-Hue-Saturation#Conversion_from_HSL_to_RGB
and:
http://en.wikipedia.org/wiki/Hue#Computing_hue_from_RGB
and:
http://www.w3.org/TR/css3-color/#hsl-color"
  [hue saturation lightness]
  (let* [h (/ hue 360.0)
         s (/ saturation 100.0)
         l (/ lightness 100.0)
         m2 (if (<= l 0.5) (* l (+ s 1))
                (- (+ l s) (* l s)))
         m1 (- (* l 2) m2)]
        (into []
              (map #(round (* 0xff %))
                   [(hue-to-rgb m1 m2 (+ h (/ 1.0 3)))
                    (hue-to-rgb m1 m2 h)
                    (hue-to-rgb m1 m2 (- h (/ 1.0 3)))]))))
  (defn rgb-to-hsl
    "Given the three RGB values, convert to HSL and return vector of
  Hue, Saturation, Lightness.

Based on algorithm described in:
http://en.wikipedia.org/wiki/Luminance-Hue-Saturation#Conversion_from_RGB_to_HSL_overview"
  [red green blue]
  (let* [r (/ red 255.0)
         g (/ green 255.0)
         b (/ blue 255.0)
         min (min r g b)
         max (max r g b)
         delta (- max min)
         l (/ (+ max min) 2.0)
         h (condp = max
                  min 0
                  r (* 60 (/ (- g b) delta))
                  g (+ 120 (* 60 (/ (- b r) delta)))
                  b (+ 240 (* 60 (/ (- r g) delta))))
         s (cond
            (= max min) 0
            (< l 0.5) (/ delta (* 2 l))
            :else (/ delta (- 2 (* 2 l))))]
        [(mod h 360) (* 100 s) (* 100 l)]))