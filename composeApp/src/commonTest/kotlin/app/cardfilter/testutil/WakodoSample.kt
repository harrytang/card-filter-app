package app.cardfilter.testutil

/**
 * The six Wakodo products from the PRD's sample file, byte for byte. Common tests cannot read resource
 * files, so the JSON lives here. `month` is a number and `ingredients` is one bilingual text; the image
 * URLs were checked on 2026-11-05: all `https`, HTTP 200, JPEG, 143-176 KB, no redirect.
 */
const val WakodoSampleJson = """[
  {
    "name": "１食分の野菜入り そのまま素材 定番野菜 [Ready-to-use Ingredients with One Meal's Serving of Vegetables: Classic Vegetables]",
    "image": "https://www.wakodo.co.jp/product/image/pdt/40194820.jpg",
    "ingredients": "にんじん（国産） [Carrot (grown in Japan)], たまねぎペースト [Onion paste], じゃがいも [Potato], かつお昆布だし [Bonito and kombu kelp stock], でん粉 [Starch]",
    "month": 7
  },
  {
    "name": "１食分の野菜入り そのまま素材 ＋鶏ささみ [Ready-to-use Ingredients with One Meal's Serving of Vegetables + Chicken Tenderloin]",
    "image": "https://www.wakodo.co.jp/product/image/pdt/40603500.jpg",
    "ingredients": "野菜（にんじん（国産）、キャベツ） [Vegetables (carrot (grown in Japan), cabbage)], じゃがいも [Potato], 鶏ほぐし身 [Shredded chicken], かつお昆布だし [Bonito and kombu kelp stock], でん粉 [Starch]",
    "month": 7
  },
  {
    "name": "１食分の野菜入り そのまま素材 根菜野菜 [Ready-to-use Ingredients with One Meal's Serving of Vegetables: Root Vegetables]",
    "image": "https://www.wakodo.co.jp/product/image/pdt/40194840.jpg",
    "ingredients": "野菜（にんじん（国産）、だいこん、ごぼう） [Vegetables (carrot (grown in Japan), daikon radish, burdock root)], かつお昆布だし [Bonito and kombu kelp stock], でん粉 [Starch]",
    "month": 9
  },
  {
    "name": "１食分の野菜入り そのまま素材 ＋レバー入り豚ミンチ [Ready-to-use Ingredients with One Meal's Serving of Vegetables + Minced Pork with Liver]",
    "image": "https://www.wakodo.co.jp/product/image/pdt/40194860.jpg",
    "ingredients": "野菜（にんじん（国産）、ごぼう） [Vegetables (carrot (grown in Japan), burdock root)], 肉加工品（豚肉、鶏レバー、でん粉、卵白（卵を含む）、食塩） [Processed meat product (pork, chicken liver, starch, egg white (contains egg), salt)], じゃがいも [Potato], かつお昆布だし [Bonito and kombu kelp stock], たまねぎペースト [Onion paste], でん粉 [Starch]",
    "month": 9
  },
  {
    "name": "１食分の野菜入り そのまま素材 ＋牛肉 [Ready-to-use Ingredients with One Meal's Serving of Vegetables + Beef]",
    "image": "https://www.wakodo.co.jp/product/image/pdt/40194870.jpg",
    "ingredients": "野菜（にんじん（国産）、とうもろこし） [Vegetables (carrot (grown in Japan), corn)], 牛肉 [Beef], かつお昆布だし [Bonito and kombu kelp stock], じゃがいも [Potato], でん粉 [Starch]",
    "month": 12
  },
  {
    "name": "１食分の野菜入り そのまま素材 ＋いわしつみれだんご [Ready-to-use Ingredients with One Meal's Serving of Vegetables + Sardine Dumplings]",
    "image": "https://www.wakodo.co.jp/product/image/pdt/40194880.jpg",
    "ingredients": "野菜（にんじん（国産）、だいこん） [Vegetables (carrot (grown in Japan), daikon radish)], じゃがいも [Potato], いわしボール（いわし、パン粉、小麦粉、たまねぎ、植物油脂、食塩） [Sardine balls (sardine, breadcrumbs, wheat flour, onion, vegetable oils and fats, salt)], かつお昆布だし [Bonito and kombu kelp stock], でん粉 [Starch]",
    "month": 12
  }
]"""
